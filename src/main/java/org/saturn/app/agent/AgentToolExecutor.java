package org.saturn.app.agent;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.llm.LlmToolCall;

@Slf4j
/**
 * Executes provider tool calls with request-local validation, safety budgets, and observations.
 *
 * <p>The executor is safe for its own virtual-thread read batches: mutable state is guarded by an
 * internal lock. It must not be reused across agent requests and must be closed to cancel remaining
 * work.
 */
public final class AgentToolExecutor implements AutoCloseable {
  private final AgentToolRegistry registry;
  private final AgentConfig config;
  private final Gson gson = new Gson();
  private final Set<String> invocationKeys = new HashSet<>();
  private final Map<String, Integer> callsByTool = new HashMap<>();
  private final Map<String, Integer> failuresByTool = new HashMap<>();
  private final Set<String> disabledTools = new HashSet<>();
  private final Set<String> successfulTools = new HashSet<>();
  private final Set<String> inFlightInvocationKeys = new HashSet<>();
  private final Set<String> allowedTools;
  private final Object stateLock = new Object();
  private final ExecutorService toolExecutor =
      Executors.newThreadPerTaskExecutor(
          Thread.ofVirtual().name("saturn-agent-tool-", 0).factory());

  public AgentToolExecutor(AgentToolRegistry registry, AgentConfig config) {
    this(registry, config, Set.of());
  }

  public AgentToolExecutor(
      AgentToolRegistry registry, AgentConfig config, Set<String> allowedTools) {
    this.registry = registry;
    this.config = config;
    this.allowedTools = Set.copyOf(allowedTools);
  }

  /**
   * Executes one call after descriptor, argument, duplicate, prerequisite, timeout, and result
   * validation. All expected failures are returned as coded {@link AgentToolResult} values.
   */
  public AgentToolResult execute(AgentContext context, LlmToolCall call) {
    if (!allowedTools.isEmpty() && !allowedTools.contains(call.name())) {
      return error(call, "TOOL_NOT_ALLOWED", "Tool is not allowed in this invocation mode");
    }
    AgentTool tool = registry.find(context, call.name()).orElse(null);
    if (tool == null) {
      return error(call, "UNKNOWN_TOOL", "Unknown tool: " + call.name());
    }
    synchronized (stateLock) {
      if (disabledTools.contains(call.name())) {
        return error(
            call, "TOOL_DISABLED", "Tool disabled after repeated failures: " + call.name());
      }
    }
    AgentToolDescriptor descriptor;
    try {
      descriptor = tool.descriptor(context);
    } catch (RuntimeException exception) {
      return error(call, "INVALID_TOOL_CONTRACT", "Invalid tool contract");
    }
    if (!tool.name().equals(descriptor.name())) {
      return error(call, "INVALID_TOOL_CONTRACT", "Tool contract name mismatch");
    }
    Set<String> missingPrerequisites;
    synchronized (stateLock) {
      missingPrerequisites = new HashSet<>(descriptor.requiredSuccessfulTools());
      missingPrerequisites.removeAll(successfulTools);
    }
    if (!missingPrerequisites.isEmpty()) {
      return error(
          call,
          "MISSING_PREREQUISITE",
          "Required tool must succeed first: " + String.join(", ", missingPrerequisites));
    }

    JsonObject arguments;
    try {
      arguments = parseArguments(call.arguments());
    } catch (JsonParseException | IllegalStateException exception) {
      recordFailure(call.name());
      return error(call, "INVALID_ARGUMENTS", "Invalid tool arguments");
    }

    String validationError =
        AgentToolSchemaValidator.validateArguments(descriptor.parameters(), arguments);
    if (validationError != null) {
      recordFailure(call.name());
      return error(call, "INVALID_ARGUMENTS", validationError);
    }

    String invocationKey = call.name() + "|" + canonicalJson(arguments);
    synchronized (stateLock) {
      if (invocationKeys.contains(invocationKey)
          || inFlightInvocationKeys.contains(invocationKey)) {
        return error(call, "DUPLICATE_TOOL_CALL", "Duplicate tool call; use the previous result");
      }
      int calls = callsByTool.getOrDefault(call.name(), 0);
      if (calls >= config.maxCallsPerTool()) {
        return error(call, "TOOL_CALL_LIMIT_REACHED", "Tool call limit reached for " + call.name());
      }
      callsByTool.put(call.name(), calls + 1);
      inFlightInvocationKeys.add(invocationKey);
    }

    try {
      AgentToolResult result = executeWithTimeout(tool, context, arguments, descriptor.timeout());
      result = result.withCallId(call.id());
      if (result.isError()) {
        recordFailure(call.name());
      } else {
        String resultError =
            AgentToolSchemaValidator.validateResult(
                descriptor.resultSchema(), parseResult(result.content()));
        if (resultError != null) {
          recordFailure(call.name());
          clearInFlight(invocationKey);
          return error(call, "INVALID_TOOL_RESULT", resultError);
        }
        synchronized (stateLock) {
          inFlightInvocationKeys.remove(invocationKey);
          invocationKeys.add(invocationKey);
          successfulTools.add(call.name());
        }
      }
      if (result.isError()) {
        clearInFlight(invocationKey);
      }
      return result;
    } catch (TimeoutException exception) {
      recordFailure(call.name());
      clearInFlight(invocationKey);
      return error(call, "TOOL_TIMEOUT", "Tool execution exceeded its configured timeout");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      recordFailure(call.name());
      clearInFlight(invocationKey);
      return error(call, "TOOL_INTERRUPTED", "Tool execution was interrupted");
    } catch (ExecutionException | RuntimeException exception) {
      recordFailure(call.name());
      clearInFlight(invocationKey);
      log.warn("Agent tool {} failed: {}", call.name(), exception.getMessage());
      return error(call, "TOOL_EXECUTION_FAILED", "Tool execution failed");
    }
  }

  /**
   * Executes calls in provider order, fanning out only contiguous read-only, idempotent calls with
   * no prerequisites. Returned observations always retain input order.
   *
   * <p>Action tools, including {@code run_command}, are order barriers and execute sequentially.
   */
  public List<AgentToolResult> executeAll(AgentContext context, List<LlmToolCall> calls) {
    List<AgentToolResult> results = new ArrayList<>();
    int index = 0;
    while (index < calls.size()) {
      int end = parallelBatchEnd(context, calls, index);
      if (end - index == 1) {
        results.add(execute(context, calls.get(index)));
      } else {
        List<Future<AgentToolResult>> futures = new ArrayList<>();
        for (int callIndex = index; callIndex < end; callIndex++) {
          LlmToolCall call = calls.get(callIndex);
          futures.add(toolExecutor.submit(() -> execute(context, call)));
        }
        for (int callIndex = index; callIndex < end; callIndex++) {
          results.add(await(calls.get(callIndex), futures.get(callIndex - index)));
        }
      }
      index = end;
    }
    return List.copyOf(results);
  }

  /** Cancels outstanding virtual-thread tool work for this request. */
  @Override
  public void close() {
    toolExecutor.shutdownNow();
  }

  private AgentToolResult executeWithTimeout(
      AgentTool tool, AgentContext context, JsonObject arguments, Duration descriptorTimeout)
      throws InterruptedException, ExecutionException, TimeoutException {
    Duration timeout = descriptorTimeout.isZero() ? config.toolTimeout() : descriptorTimeout;
    Future<AgentToolResult> future = toolExecutor.submit(() -> tool.execute(context, arguments));
    try {
      AgentToolResult result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (result == null) {
        throw new IllegalStateException("Tool returned no result");
      }
      return result;
    } catch (TimeoutException exception) {
      future.cancel(true);
      throw exception;
    }
  }

  private AgentToolResult error(LlmToolCall call, String code, String message) {
    return AgentToolResult.error(call.id(), call.name(), code, message);
  }

  private int parallelBatchEnd(AgentContext context, List<LlmToolCall> calls, int start) {
    if (!isParallelSafe(context, calls.get(start))) {
      return start + 1;
    }
    int end = start + 1;
    while (end < calls.size() && isParallelSafe(context, calls.get(end))) {
      end++;
    }
    return end;
  }

  private boolean isParallelSafe(AgentContext context, LlmToolCall call) {
    AgentTool tool = registry.find(context, call.name()).orElse(null);
    if (tool == null) {
      return false;
    }
    try {
      AgentToolDescriptor descriptor = tool.descriptor(context);
      return descriptor.isReadOnly()
          && descriptor.isIdempotent()
          && descriptor.requiredSuccessfulTools().isEmpty();
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private AgentToolResult await(LlmToolCall call, Future<AgentToolResult> future) {
    try {
      return future.get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return error(call, "TOOL_INTERRUPTED", "Tool batch execution was interrupted");
    } catch (ExecutionException exception) {
      return error(call, "TOOL_EXECUTION_FAILED", "Tool batch execution failed");
    }
  }

  private void clearInFlight(String invocationKey) {
    synchronized (stateLock) {
      inFlightInvocationKeys.remove(invocationKey);
    }
  }

  private JsonObject parseArguments(String rawArguments) {
    if (rawArguments == null || rawArguments.isBlank()) {
      return new JsonObject();
    }
    JsonObject arguments = gson.fromJson(rawArguments, JsonObject.class);
    if (arguments == null) {
      throw new JsonParseException("Tool arguments must be a JSON object");
    }
    return arguments;
  }

  private JsonElement parseResult(String content) {
    if (content == null) {
      return com.google.gson.JsonNull.INSTANCE;
    }
    try {
      return JsonParser.parseString(content);
    } catch (RuntimeException ignored) {
      return new com.google.gson.JsonPrimitive(content);
    }
  }

  private String canonicalJson(JsonElement element) {
    if (element.isJsonObject()) {
      Map<String, JsonElement> sorted = new TreeMap<>();
      for (var entry : element.getAsJsonObject().entrySet()) {
        sorted.put(entry.getKey(), entry.getValue());
      }
      StringJoiner result = new StringJoiner(",", "{", "}");
      for (var entry : sorted.entrySet()) {
        result.add("%s:%s".formatted(gson.toJson(entry.getKey()), canonicalJson(entry.getValue())));
      }
      return result.toString();
    }
    if (element.isJsonArray()) {
      StringJoiner result = new StringJoiner(",", "[", "]");
      for (JsonElement value : element.getAsJsonArray()) {
        result.add(canonicalJson(value));
      }
      return result.toString();
    }
    return gson.toJson(element);
  }

  private void recordFailure(String toolName) {
    synchronized (stateLock) {
      int failures = failuresByTool.merge(toolName, 1, Integer::sum);
      if (failures >= config.maxToolFailures()) {
        disabledTools.add(toolName);
      }
    }
  }
}
