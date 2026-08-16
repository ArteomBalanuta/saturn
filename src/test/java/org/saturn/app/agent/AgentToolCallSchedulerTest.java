package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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

  private AgentScheduledToolCall scheduled(LlmToolCall call, AgentToolExecutionMode executionMode) {
    return new AgentScheduledToolCall(call, executionMode);
  }
}
