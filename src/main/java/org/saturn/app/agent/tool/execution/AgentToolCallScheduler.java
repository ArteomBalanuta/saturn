package org.saturn.app.agent.tool.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.llm.LlmToolCall;

/** Request-local scheduler for contiguous, provider-ordered tool-call batches. */
@Slf4j
final class AgentToolCallScheduler implements AutoCloseable {
  private final ExecutorService executor;
  private final boolean ownsExecutor;
  private final AgentToolExecutionPolicy policy;

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
    this.policy = new AgentToolExecutionPolicy();
  }

  List<AgentToolResult> executeAll(
      List<AgentScheduledToolCall> calls, ToolCallExecution execution) {
    return executeAll(calls, AgentToolBatchContext.unlimited(), execution);
  }

  List<AgentToolResult> executeAll(
      List<AgentScheduledToolCall> calls,
      AgentToolBatchContext batch,
      ToolCallExecution execution) {
    List<AgentToolResult> results = new ArrayList<>();
    int index = 0;
    while (index < calls.size()) {
      int end = parallelBatchEnd(calls, index);
      if (end - index == 1) {
        results.add(execute(calls.get(index).call(), batch, execution));
      } else {
        results.addAll(executeParallel(calls.subList(index, end), batch, execution));
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

  private int parallelBatchEnd(List<AgentScheduledToolCall> calls, int start) {
    if (!calls.get(start).isParallelRead()) {
      return start + 1;
    }
    int end = start + 1;
    while (end < calls.size()
        && calls.get(end).isParallelRead()
        && compatibleWithBatch(calls, start, end)) {
      end++;
    }
    return end;
  }

  private boolean compatibleWithBatch(
      List<AgentScheduledToolCall> calls, int start, int candidate) {
    for (int index = start; index < candidate; index++) {
      if (!policy.compatible(calls.get(index), calls.get(candidate))) {
        return false;
      }
    }
    return true;
  }

  private List<AgentToolResult> executeParallel(
      List<AgentScheduledToolCall> calls,
      AgentToolBatchContext batch,
      ToolCallExecution execution) {
    List<Future<AgentToolResult>> futures = new ArrayList<>();
    for (AgentScheduledToolCall scheduledCall : calls) {
      if (batch.cancellation().isCancelled() || batch.expired()) {
        futures.add(
            java.util.concurrent.CompletableFuture.completedFuture(
                error(scheduledCall.call(), batchCode(batch), batchMessage(batch))));
      } else {
        futures.add(executor.submit(() -> execute(scheduledCall.call(), batch, execution)));
      }
    }
    List<AgentToolResult> results = new ArrayList<>();
    for (int index = 0; index < calls.size(); index++) {
      results.add(await(calls.get(index).call(), futures.get(index), batch));
    }
    return results;
  }

  private static AgentToolResult execute(LlmToolCall call, ToolCallExecution execution) {
    return execute(call, AgentToolBatchContext.unlimited(), execution);
  }

  private static AgentToolResult execute(
      LlmToolCall call, AgentToolBatchContext batch, ToolCallExecution execution) {
    if (batch.cancellation().isCancelled() || batch.expired()) {
      return error(call, batchCode(batch), batchMessage(batch));
    }
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

  private static AgentToolResult await(
      LlmToolCall call, Future<AgentToolResult> future, AgentToolBatchContext batch) {
    try {
      long remaining =
          batch.deadline().equals(Instant.MAX)
              ? Long.MAX_VALUE
              : Math.max(1L, Duration.between(Instant.now(), batch.deadline()).toNanos());
      return future.get(remaining, TimeUnit.NANOSECONDS);
    } catch (InterruptedException exception) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      if (batch.cancellation().isCancelled() || batch.expired()) {
        return error(call, batchCode(batch), batchMessage(batch));
      }
      return error(call, "TOOL_INTERRUPTED", "Tool batch execution was interrupted");
    } catch (TimeoutException exception) {
      future.cancel(true);
      batch.cancellation().cancelDeadline();
      return error(call, "TOOL_BATCH_DEADLINE", "Tool batch deadline exceeded");
    } catch (ExecutionException exception) {
      return error(call, "TOOL_EXECUTION_FAILED", "Tool batch execution failed");
    } catch (CancellationException exception) {
      if (batch.cancellation().isCancelled() || batch.expired()) {
        return error(call, batchCode(batch), batchMessage(batch));
      }
      return error(call, "TOOL_INTERRUPTED", "Tool batch execution was interrupted");
    }
  }

  private static String batchCode(AgentToolBatchContext batch) {
    return batch.expired()
            || batch.cancellationReason() == AgentToolBatchContext.CancellationReason.DEADLINE
        ? "TOOL_BATCH_DEADLINE"
        : "TOOL_BATCH_CANCELLED";
  }

  private static String batchMessage(AgentToolBatchContext batch) {
    return "TOOL_BATCH_DEADLINE".equals(batchCode(batch))
        ? "Tool batch deadline exceeded"
        : "Tool batch execution was cancelled";
  }

  private static AgentToolResult error(LlmToolCall call, String code, String message) {
    return AgentToolResult.error(call.id(), call.name(), code, message);
  }

  @FunctionalInterface
  interface ToolCallExecution {
    AgentToolResult execute(LlmToolCall call) throws Exception;
  }
}
