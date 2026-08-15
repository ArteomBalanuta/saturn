package org.saturn.app.agent;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
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
public final class AgentToolExecutor implements AutoCloseable {
  private final AgentToolRegistry registry;
  private final AgentConfig config;
  private final Gson gson = new Gson();
  private final Set<String> invocationKeys = new HashSet<>();
  private final Map<String, Integer> callsByTool = new HashMap<>();
  private final Map<String, Integer> failuresByTool = new HashMap<>();
  private final Set<String> disabledTools = new HashSet<>();
  private final Set<String> successfulTools = new HashSet<>();
  private final Set<String> allowedTools;
  private final ExecutorService toolExecutor =
      Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("saturn-agent-tool-", 0).factory());

  public AgentToolExecutor(AgentToolRegistry registry, AgentConfig config) {
    this(registry, config, Set.of());
  }

  public AgentToolExecutor(AgentToolRegistry registry, AgentConfig config, Set<String> allowedTools) {
    this.registry = registry;
    this.config = config;
    this.allowedTools = Set.copyOf(allowedTools);
  }

  public AgentToolResult execute(AgentContext context, LlmToolCall call) {
    if (!allowedTools.isEmpty() && !allowedTools.contains(call.name())) {
      return error(call, "TOOL_NOT_ALLOWED", "Tool is not allowed in this invocation mode");
    }
    AgentTool tool = registry.find(context, call.name()).orElse(null);
    if (tool == null) {
      return error(call, "UNKNOWN_TOOL", "Unknown tool: " + call.name());
    }
    if (disabledTools.contains(call.name())) {
      return error(call, "TOOL_DISABLED", "Tool disabled after repeated failures: " + call.name());
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
    Set<String> missingPrerequisites = new HashSet<>(descriptor.requiredSuccessfulTools());
    missingPrerequisites.removeAll(successfulTools);
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

    String validationError = AgentToolSchemaValidator.validateArguments(descriptor.parameters(), arguments);
    if (validationError != null) {
      recordFailure(call.name());
      return error(call, "INVALID_ARGUMENTS", validationError);
    }

    String invocationKey = call.name() + "|" + canonicalJson(arguments);
    if (invocationKeys.contains(invocationKey)) {
      return error(call, "DUPLICATE_TOOL_CALL", "Duplicate tool call; use the previous result");
    }

    int calls = callsByTool.getOrDefault(call.name(), 0);
    if (calls >= config.maxCallsPerTool()) {
      return error(call, "TOOL_CALL_LIMIT_REACHED", "Tool call limit reached for " + call.name());
    }
    callsByTool.put(call.name(), calls + 1);

    try {
      AgentToolResult result = executeWithTimeout(tool, context, arguments, descriptor.timeout());
      result = result.withCallId(call.id());
      if (result.isError()) {
        recordFailure(call.name());
      } else {
        String resultError =
            AgentToolSchemaValidator.validateResult(descriptor.resultSchema(), parseResult(result.content()));
        if (resultError != null) {
          recordFailure(call.name());
          return error(call, "INVALID_TOOL_RESULT", resultError);
        }
        invocationKeys.add(invocationKey);
        successfulTools.add(call.name());
      }
      return result;
    } catch (TimeoutException exception) {
      recordFailure(call.name());
      return error(call, "TOOL_TIMEOUT", "Tool execution exceeded its configured timeout");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      recordFailure(call.name());
      return error(call, "TOOL_INTERRUPTED", "Tool execution was interrupted");
    } catch (ExecutionException | RuntimeException exception) {
      recordFailure(call.name());
      log.warn("Agent tool {} failed: {}", call.name(), exception.getMessage());
      return error(call, "TOOL_EXECUTION_FAILED", "Tool execution failed");
    }
  }

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
    int failures = failuresByTool.merge(toolName, 1, Integer::sum);
    if (failures >= config.maxToolFailures()) {
      disabledTools.add(toolName);
    }
  }
}
