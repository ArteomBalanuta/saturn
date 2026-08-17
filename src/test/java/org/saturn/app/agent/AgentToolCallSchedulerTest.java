package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmToolCall;

class AgentToolCallSchedulerTest {
  @Test
  void fansOutOnlyConsecutiveParallelSafeCallsAndPreservesProviderOrder() throws Exception {
    LlmToolCall first = new LlmToolCall("first", "room_users", "{}");
    LlmToolCall second = new LlmToolCall("second", "user_message_history", "{}");
    LlmToolCall command = new LlmToolCall("command", "run_command", "{}");
    CountDownLatch bothReadsStarted = new CountDownLatch(2);
    CountDownLatch releaseReads = new CountDownLatch(1);
    AtomicInteger completedReads = new AtomicInteger();

    try (AgentToolCallScheduler scheduler = new AgentToolCallScheduler();
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var future =
          executor.submit(
              () ->
                  scheduler.executeAll(
                      List.of(
                          scheduled(first, AgentToolExecutionMode.PARALLEL_READ),
                          scheduled(second, AgentToolExecutionMode.PARALLEL_READ),
                          scheduled(command, AgentToolExecutionMode.SEQUENTIAL_ACTION)),
                      call -> {
                        if (!"run_command".equals(call.name())) {
                          bothReadsStarted.countDown();
                          assertTrue(releaseReads.await(1, TimeUnit.SECONDS));
                          completedReads.incrementAndGet();
                        } else {
                          assertEquals(2, completedReads.get());
                        }
                        return AgentToolResult.success(call.name(), call.name());
                      }));
      assertTrue(bothReadsStarted.await(1, TimeUnit.SECONDS));
      releaseReads.countDown();
      List<AgentToolResult> results = future.get(1, TimeUnit.SECONDS);
      assertEquals(
          List.of("room_users", "user_message_history", "run_command"),
          results.stream().map(AgentToolResult::toolName).toList());
    }
  }

  @Test
  void dependentReadIsAnOrderBarrierAndLaterReadsCanFanOut() throws Exception {
    LlmToolCall parallel = new LlmToolCall("parallel", "room_users", "{}");
    LlmToolCall dependent = new LlmToolCall("dependent", "user_message_history", "{}");
    LlmToolCall later = new LlmToolCall("later", "room_users", "{}");
    AtomicInteger completed = new AtomicInteger();

    try (AgentToolCallScheduler scheduler = new AgentToolCallScheduler()) {
      List<AgentToolResult> results =
          scheduler.executeAll(
              List.of(
                  scheduled(parallel, AgentToolExecutionMode.PARALLEL_READ),
                  scheduled(dependent, AgentToolExecutionMode.SEQUENTIAL_DEPENDENT_READ),
                  scheduled(later, AgentToolExecutionMode.PARALLEL_READ)),
              call -> {
                if ("user_message_history".equals(call.name())) {
                  assertEquals(1, completed.get());
                }
                completed.incrementAndGet();
                return AgentToolResult.success(call.name(), call.name());
              });

      assertEquals(3, completed.get());
      assertEquals(
          List.of("room_users", "user_message_history", "room_users"),
          results.stream().map(AgentToolResult::toolName).toList());
    }
  }

  @Test
  void convertsParallelExecutionFailuresToCodedResultsAndContinues() {
    LlmToolCall failed = new LlmToolCall("failed", "room_users", "{}");
    LlmToolCall successful = new LlmToolCall("successful", "user_message_history", "{}");

    try (AgentToolCallScheduler scheduler = new AgentToolCallScheduler()) {
      List<AgentToolResult> results =
          scheduler.executeAll(
              List.of(
                  scheduled(failed, AgentToolExecutionMode.PARALLEL_READ),
                  scheduled(successful, AgentToolExecutionMode.PARALLEL_READ)),
              call -> {
                if (call.id().equals("failed")) {
                  throw new IllegalStateException("internal failure");
                }
                return AgentToolResult.success(call.name(), "ok");
              });

      assertTrue(results.get(0).isError());
      assertEquals("TOOL_EXECUTION_FAILED", results.get(0).errorCode());
      assertEquals("ok", results.get(1).content());
    }
  }

  @Test
  void returnsAnImmutableEmptyResultForNoCalls() {
    try (AgentToolCallScheduler scheduler = new AgentToolCallScheduler()) {
      List<AgentToolResult> results = scheduler.executeAll(List.of(), call -> null);

      assertTrue(results.isEmpty());
      assertTrue(
          org.junit.jupiter.api.Assertions.assertThrows(
                  UnsupportedOperationException.class,
                  () -> results.add(AgentToolResult.success("tool", "value")))
              != null);
    }
  }

  @Test
  void convertsNullToolResultsToCodedFailures() {
    LlmToolCall sequential = new LlmToolCall("sequential", "run_command", "{}");
    LlmToolCall parallel = new LlmToolCall("parallel", "room_users", "{}");

    try (AgentToolCallScheduler scheduler = new AgentToolCallScheduler()) {
      List<AgentToolResult> results =
          scheduler.executeAll(
              List.of(
                  scheduled(sequential, AgentToolExecutionMode.SEQUENTIAL_ACTION),
                  scheduled(parallel, AgentToolExecutionMode.PARALLEL_READ)),
              call -> null);

      assertEquals(2, results.size());
      assertTrue(results.stream().allMatch(AgentToolResult::isError));
      assertTrue(
          results.stream().allMatch(result -> "TOOL_EXECUTION_FAILED".equals(result.errorCode())));
    }
  }

  @Test
  void convertsCancelledParallelFuturesToCodedInterruptions() {
    LlmToolCall first = new LlmToolCall("first", "room_users", "{}");
    LlmToolCall second = new LlmToolCall("second", "user_message_history", "{}");

    try (AgentToolCallScheduler scheduler =
        new AgentToolCallScheduler(new CancelledFutureExecutor())) {
      List<AgentToolResult> results =
          scheduler.executeAll(
              List.of(
                  scheduled(first, AgentToolExecutionMode.PARALLEL_READ),
                  scheduled(second, AgentToolExecutionMode.PARALLEL_READ)),
              call -> AgentToolResult.success(call.name(), "unexpected"));

      assertEquals(2, results.size());
      assertTrue(
          results.stream().allMatch(result -> "TOOL_INTERRUPTED".equals(result.errorCode())));
    }
  }

  @Test
  void convertsSequentialInterruptionsToCodedResultsAndRestoresInterruptFlag() {
    LlmToolCall call = new LlmToolCall("interrupted", "run_command", "{}");

    try {
      try (AgentToolCallScheduler scheduler = new AgentToolCallScheduler()) {
        List<AgentToolResult> results =
            scheduler.executeAll(
                List.of(scheduled(call, AgentToolExecutionMode.SEQUENTIAL_ACTION)),
                ignored -> {
                  throw new InterruptedException("interrupted");
                });

        assertEquals("TOOL_INTERRUPTED", results.getFirst().errorCode());
        assertTrue(Thread.currentThread().isInterrupted());
      }
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void convertsInterruptedParallelWaitsToCodedResultsAndRestoresInterruptFlag() {
    LlmToolCall first = new LlmToolCall("first", "room_users", "{}");
    LlmToolCall second = new LlmToolCall("second", "user_message_history", "{}");

    try {
      try (AgentToolCallScheduler scheduler =
          new AgentToolCallScheduler(new InterruptedFutureExecutor())) {
        List<AgentToolResult> results =
            scheduler.executeAll(
                List.of(
                    scheduled(first, AgentToolExecutionMode.PARALLEL_READ),
                    scheduled(second, AgentToolExecutionMode.PARALLEL_READ)),
                call -> AgentToolResult.success(call.name(), "unexpected"));

        assertEquals(2, results.size());
        assertTrue(
            results.stream().allMatch(result -> "TOOL_INTERRUPTED".equals(result.errorCode())));
        assertTrue(Thread.currentThread().isInterrupted());
      }
    } finally {
      Thread.interrupted();
    }
  }

  private AgentScheduledToolCall scheduled(LlmToolCall call, AgentToolExecutionMode executionMode) {
    return new AgentScheduledToolCall(call, executionMode);
  }

  private static class CancelledFutureExecutor extends AbstractExecutorService {
    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return false;
    }

    @Override
    public void execute(Runnable command) {}

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      FutureTask<T> future = new FutureTask<>(task);
      future.cancel(false);
      return future;
    }
  }

  private static final class InterruptedFutureExecutor extends CancelledFutureExecutor {
    @Override
    public <T> Future<T> submit(Callable<T> task) {
      return new Future<>() {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
          return false;
        }

        @Override
        public boolean isCancelled() {
          return false;
        }

        @Override
        public boolean isDone() {
          return false;
        }

        @Override
        public T get() throws InterruptedException {
          throw new InterruptedException("interrupted");
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException {
          throw new InterruptedException("interrupted");
        }
      };
    }
  }
}
