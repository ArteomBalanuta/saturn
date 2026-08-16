package org.saturn.app.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.llm.LlmToolCall;

/**
 * Request-local scheduling strategy for provider tool calls.
 *
 * <p>Only adjacent calls accepted by the supplied parallel-safety predicate fan out. Results are
 * always returned in provider order, allowing callers to append stable observations to the model
 * conversation.
 */
@Slf4j
final class AgentToolCallScheduler implements AutoCloseable {
  private final ExecutorService executor;
  private final boolean ownsExecutor;

  AgentToolCallScheduler() {
    this(
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("saturn-agent-schedule-", 0).factory()),
        true);
  }

  AgentToolCallScheduler(ExecutorService executor) {
    this(executor, false);
  }

  private AgentToolCallScheduler(ExecutorService executor, boolean ownsExecutor) {
    this.executor = executor;
    this.ownsExecutor = ownsExecutor;
  }

  List<AgentToolResult> executeAll(
      List<AgentScheduledToolCall> calls, ToolCallExecution execution) {
    List<AgentToolResult> results = new ArrayList<>();
    int index = 0;
    while (index < calls.size()) {
      int end = parallelBatchEnd(calls, index);
      if (end - index == 1) {
        results.add(execute(calls.get(index).call(), execution));
      } else {
        results.addAll(executeParallel(calls.subList(index, end), execution));
      }
      index = end;
    }
    return List.copyOf(results);
  }

  @Override
  public void close() {
    if (ownsExecutor) {
      executor.shutdownNow();
    }
  }

  private static int parallelBatchEnd(List<AgentScheduledToolCall> calls, int start) {
    if (!calls.get(start).isParallelRead()) {
      return start + 1;
    }
    int end = start + 1;
    while (end < calls.size() && calls.get(end).isParallelRead()) {
      end++;
    }
    return end;
  }

  private List<AgentToolResult> executeParallel(
      List<AgentScheduledToolCall> calls, ToolCallExecution execution) {
    List<Future<AgentToolResult>> futures = new ArrayList<>();
    for (AgentScheduledToolCall scheduledCall : calls) {
      futures.add(executor.submit(() -> execute(scheduledCall.call(), execution)));
    }
    List<AgentToolResult> results = new ArrayList<>();
    for (int index = 0; index < calls.size(); index++) {
      results.add(await(calls.get(index).call(), futures.get(index)));
    }
    return results;
  }

  private static AgentToolResult execute(LlmToolCall call, ToolCallExecution execution) {
    try {
      AgentToolResult result = execution.execute(call);
      return result == null
          ? error(call, "TOOL_EXECUTION_FAILED", "Tool execution returned no result")
          : result;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return error(call, "TOOL_INTERRUPTED", "Tool batch execution was interrupted");
    } catch (Exception exception) {
      log.warn(
          "Agent scheduled tool execution failed, tool={}, callId={}",
          call.name(),
          call.id(),
          exception);
      return error(call, "TOOL_EXECUTION_FAILED", "Tool batch execution failed");
    }
  }

  private static AgentToolResult await(LlmToolCall call, Future<AgentToolResult> future) {
    try {
      return future.get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return error(call, "TOOL_INTERRUPTED", "Tool batch execution was interrupted");
    } catch (ExecutionException exception) {
      log.warn(
          "Agent scheduled tool future failed, tool={}, callId={}",
          call.name(),
          call.id(),
          exception.getCause() == null ? exception : exception.getCause());
      return error(call, "TOOL_EXECUTION_FAILED", "Tool batch execution failed");
    } catch (CancellationException exception) {
      return error(call, "TOOL_INTERRUPTED", "Tool batch execution was interrupted");
    }
  }

  private static AgentToolResult error(LlmToolCall call, String code, String message) {
    return AgentToolResult.error(call.id(), call.name(), code, message);
  }

  @FunctionalInterface
  interface ToolCallExecution {
    AgentToolResult execute(LlmToolCall call) throws Exception;
  }
}
