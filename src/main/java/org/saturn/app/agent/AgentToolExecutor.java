package org.saturn.app.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
  private final Set<String> allowedTools;
  private final AgentToolCallValidator validator;
  private final AgentToolExecutionLedger ledger = new AgentToolExecutionLedger();
  private final ExecutorService toolExecutor =
      Executors.newThreadPerTaskExecutor(
          Thread.ofVirtual().name("saturn-agent-tool-", 0).factory());
  private final AgentToolInvoker toolInvoker = new AgentToolInvoker(toolExecutor);
  private final AgentToolCallScheduler scheduler = new AgentToolCallScheduler(toolExecutor);
  private final AgentToolExecutionPolicy executionPolicy = new AgentToolExecutionPolicy();

  public AgentToolExecutor(AgentToolRegistry registry, AgentConfig config) {
    this(registry, config, Set.of());
  }

  public AgentToolExecutor(
      AgentToolRegistry registry, AgentConfig config, Set<String> allowedTools) {
    this.registry = registry;
    this.config = config;
    this.allowedTools = Set.copyOf(allowedTools);
    this.validator = new AgentToolCallValidator(registry);
  }

  /**
   * Executes one call after descriptor, argument, duplicate, prerequisite, timeout, and result
   * validation. All expected failures are returned as coded {@link AgentToolResult} values.
   */
  public AgentToolResult execute(AgentContext context, LlmToolCall call) {
    return execute(context, call, null);
  }

  private AgentToolResult execute(
      AgentContext context, LlmToolCall call, AgentToolDescriptor classifiedDescriptor) {
    if (ledger.isDisabled(call.name())) {
      return error(call, "TOOL_DISABLED", "Tool disabled after repeated failures: " + call.name());
    }
    AgentToolCallValidator.Result validation =
        validator.validate(context, call, allowedTools, classifiedDescriptor);
    if (!validation.isValid()) {
      AgentToolResult failure = validation.error();
      if ("INVALID_ARGUMENTS".equals(failure.errorCode())) {
        ledger.recordValidationFailure(call.name(), config.maxToolFailures());
      }
      return failure;
    }
    ValidatedToolCall validated = validation.call();
    Set<String> missingPrerequisites = ledger.missingPrerequisites(validated.descriptor());
    if (!missingPrerequisites.isEmpty()) {
      return error(
          call,
          "MISSING_PREREQUISITE",
          "Required tool must succeed first: " + String.join(", ", missingPrerequisites));
    }

    AgentToolExecutionLedger.Reservation reservation =
        ledger.reserve(validated.invocationKey(), call.name(), config.maxCallsPerTool());
    if (reservation == AgentToolExecutionLedger.Reservation.DUPLICATE) {
      return error(call, "DUPLICATE_TOOL_CALL", "Duplicate tool call; use the previous result");
    }
    if (reservation == AgentToolExecutionLedger.Reservation.LIMIT_REACHED) {
      return error(call, "TOOL_CALL_LIMIT_REACHED", "Tool call limit reached for " + call.name());
    }

    try {
      AgentToolResult result =
          executeWithTimeout(
              validated.tool(), context, validated.arguments(), validated.descriptor().timeout());
      result = result.withCallId(call.id());
      if (result.isError()) {
        ledger.recordFailure(validated.invocationKey(), call.name(), config.maxToolFailures());
      } else {
        String resultError =
            AgentToolSchemaValidator.validateResult(
                validated.descriptor().resultSchema(), parseResult(result.content()));
        if (resultError != null) {
          ledger.recordFailure(validated.invocationKey(), call.name(), config.maxToolFailures());
          return error(call, "INVALID_TOOL_RESULT", resultError);
        }
        ledger.recordSuccess(validated.invocationKey(), call.name());
      }
      return result;
    } catch (TimeoutException exception) {
      ledger.recordFailure(validated.invocationKey(), call.name(), config.maxToolFailures());
      return error(call, "TOOL_TIMEOUT", "Tool execution exceeded its configured timeout");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      ledger.recordFailure(validated.invocationKey(), call.name(), config.maxToolFailures());
      return error(call, "TOOL_INTERRUPTED", "Tool execution was interrupted");
    } catch (ExecutionException | RuntimeException exception) {
      ledger.recordFailure(validated.invocationKey(), call.name(), config.maxToolFailures());
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
    List<AgentScheduledToolCall> scheduledCalls = new ArrayList<>();
    Map<LlmToolCall, Classification> classifications = new IdentityHashMap<>();
    for (LlmToolCall call : calls) {
      Classification classification = classify(context, call);
      scheduledCalls.add(classification.scheduledCall());
      classifications.put(call, classification);
    }
    return scheduler.executeAll(
        scheduledCalls,
        call -> {
          Classification classification = classifications.get(call);
          return classification.error() != null
              ? classification.error()
              : execute(context, call, classification.descriptor());
        });
  }

  /** Cancels outstanding virtual-thread tool work for this request. */
  @Override
  public void close() {
    scheduler.close();
    toolInvoker.close();
  }

  private AgentToolResult executeWithTimeout(
      AgentTool tool, AgentContext context, JsonObject arguments, Duration descriptorTimeout)
      throws InterruptedException, ExecutionException, TimeoutException {
    Duration timeout = descriptorTimeout.isZero() ? config.toolTimeout() : descriptorTimeout;
    return toolInvoker.invoke(tool, context, arguments, timeout);
  }

  private AgentToolResult error(LlmToolCall call, String code, String message) {
    return AgentToolResult.error(call.id(), call.name(), code, message);
  }

  private Classification classify(AgentContext context, LlmToolCall call) {
    AgentTool tool = registry.find(context, call.name()).orElse(null);
    if (tool == null) {
      return new Classification(
          new AgentScheduledToolCall(call, AgentToolExecutionMode.SEQUENTIAL_ACTION), null, null);
    }
    try {
      AgentToolDescriptor descriptor = tool.descriptor(context);
      return new Classification(
          new AgentScheduledToolCall(call, executionPolicy.classify(descriptor)), descriptor, null);
    } catch (RuntimeException exception) {
      return new Classification(
          new AgentScheduledToolCall(call, AgentToolExecutionMode.SEQUENTIAL_ACTION),
          null,
          AgentToolResult.error(
              call.id(), call.name(), "INVALID_TOOL_CONTRACT", "Invalid tool contract"));
    }
  }

  private record Classification(
      AgentScheduledToolCall scheduledCall,
      AgentToolDescriptor descriptor,
      AgentToolResult error) {}

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
}
