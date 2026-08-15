package org.saturn.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.AgentConfig;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentInvocation;
import org.saturn.app.agent.AgentResult;
import org.saturn.app.agent.AgentRouter;

class AgentServiceImplTest {
  @Test
  void boundsConcurrentRequestsAndRepliesWhenRoutingCompletes() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AgentRouter router =
        invocation -> {
          entered.countDown();
          try {
            release.await();
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
          return new AgentResult("correlation", "answer");
        };
    ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
    AgentServiceImpl service = new AgentServiceImpl(config(true, 1), router, new OutService(queue));

    assertTrue(service.submit(invocation(false)));
    assertTrue(entered.await(1, TimeUnit.SECONDS));
    assertFalse(service.submit(invocation(false)));
    release.countDown();

    awaitQueueSize(queue, 2);
    assertTrue(queue.stream().anyMatch(value -> value.contains("busy")));
    assertTrue(queue.stream().anyMatch(value -> value.equals("@alice answer")));
    service.close();
  }

  @Test
  void preservesWhisperAndRejectsDisabledOrClosedService() throws Exception {
    ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
    AgentServiceImpl disabled =
        new AgentServiceImpl(
            config(false, 1), invocation -> new AgentResult("id", "unused"), new OutService(queue));

    assertFalse(disabled.submit(invocation(true)));
    assertTrue(queue.take().contains("/whisper @alice"));
    disabled.close();
    assertFalse(disabled.submit(invocation(true)));
  }

  @Test
  void repliesWithStableErrorWhenRouterFailsUnexpectedly() throws Exception {
    ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
    AgentServiceImpl service =
        new AgentServiceImpl(
            config(true, 1),
            invocation -> {
              throw new IllegalStateException("implementation detail");
            },
            new OutService(queue));

    assertTrue(service.submit(invocation(false)));

    String reply = queue.poll(2, TimeUnit.SECONDS);
    assertTrue(reply != null && reply.contains("could not answer"));
    service.close();
  }

  @Test
  void rejectsSubmissionAfterShutdown() throws Exception {
    ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
    AgentServiceImpl service =
        new AgentServiceImpl(
            config(true, 1), invocation -> new AgentResult("id", "unused"), new OutService(queue));

    service.close();

    assertFalse(service.submit(invocation(false)));
    assertTrue(queue.take().contains("shutting down"));
  }

  private AgentInvocation invocation(boolean whisper) {
    return new AgentInvocation(
        new AgentContext("programming", "alice", "trip-a", "hash-a", whisper, List.of("alice")),
        "question");
  }

  private AgentConfig config(boolean enabled, int concurrent) {
    return new AgentConfig(
        enabled,
        URI.create("http://localhost"),
        Optional.empty(),
        "",
        Duration.ofSeconds(1),
        concurrent,
        4,
        2,
        2,
        100,
        100,
        2,
        Duration.ofHours(1),
        0,
        Duration.ZERO);
  }

  private static void awaitQueueSize(ArrayBlockingQueue<String> queue, int expected)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (queue.size() < expected && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
  }
}
