package org.saturn.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.AgentConfig;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentInvocation;
import org.saturn.app.agent.AgentInvocationMode;
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
    AgentServiceImpl service =
        new AgentServiceImpl(config(true, 1), router, new OutService(queue), () -> {});

    assertTrue(service.submit(invocation(false)));
    assertTrue(entered.await(1, TimeUnit.SECONDS));
    assertFalse(service.submit(invocation(false)));
    release.countDown();

    awaitQueueSize(queue, 3);
    assertTrue(queue.stream().anyMatch(value -> value.contains("busy")));
    assertTrue(queue.stream().anyMatch(value -> value.contains("completed: answer")));
    service.close();
  }

  @Test
  void emitsVisibleProgressAndStableRequestIdForDirectRequests() throws Exception {
    ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
    AgentInvocation invocation =
        new AgentInvocation(
            "request-123456789",
            new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of("alice")),
            "question");
    AgentServiceImpl service =
        new AgentServiceImpl(
            config(true, 1),
            request -> new AgentResult(request.requestId(), "answer"),
            new OutService(queue),
            () -> {});

    try {
      assertTrue(service.submit(invocation));

      awaitQueueSize(queue, 2);
      List<String> messages = List.copyOf(queue);
      assertTrue(messages.stream().anyMatch(message -> message.contains("working")));
      assertTrue(messages.stream().anyMatch(message -> message.contains("answer")));
      assertTrue(messages.stream().allMatch(message -> message.contains("request-1234")));
    } finally {
      service.close();
    }
  }

  @Test
  void doesNotFlushProgressOnTheSubmittingThread() throws Exception {
    CountDownLatch progressFlushEntered = new CountDownLatch(1);
    CountDownLatch releaseProgressFlush = new CountDownLatch(1);
    CountDownLatch routerEntered = new CountDownLatch(1);
    AtomicInteger flushes = new AtomicInteger();
    AgentServiceImpl service =
        new AgentServiceImpl(
            config(true, 1),
            invocation -> {
              routerEntered.countDown();
              return new AgentResult(invocation.requestId(), "answer");
            },
            new OutService(new ArrayBlockingQueue<>(10)),
            () -> {
              if (flushes.getAndIncrement() == 0) {
                progressFlushEntered.countDown();
                try {
                  releaseProgressFlush.await();
                } catch (InterruptedException exception) {
                  Thread.currentThread().interrupt();
                }
              }
            });

    try {
      assertTimeoutPreemptively(
          Duration.ofMillis(200), () -> assertTrue(service.submit(invocation(false))));
      assertTrue(progressFlushEntered.await(1, TimeUnit.SECONDS));
      assertFalse(routerEntered.await(100, TimeUnit.MILLISECONDS));
    } finally {
      releaseProgressFlush.countDown();
      service.close();
    }
  }

  @Test
  void executesAndFlushesSharedSessionRequestsInSubmissionOrder() throws Exception {
    CountDownLatch firstEntered = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    CountDownLatch secondEntered = new CountDownLatch(1);
    List<String> routed = new CopyOnWriteArrayList<>();
    AgentRouter router =
        invocation -> {
          routed.add(invocation.prompt());
          if ("first".equals(invocation.prompt())) {
            firstEntered.countDown();
            try {
              releaseFirst.await();
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            }
          } else {
            secondEntered.countDown();
          }
          return new AgentResult(invocation.requestId(), invocation.prompt() + " answer");
        };
    ArrayBlockingQueue<String> replies = new ArrayBlockingQueue<>(10);
    List<String> flushed = new CopyOnWriteArrayList<>();
    AgentServiceImpl service =
        new AgentServiceImpl(
            config(true, 2),
            router,
            new OutService(replies),
            () -> {
              String reply;
              while ((reply = replies.poll()) != null) {
                flushed.add(reply);
              }
            });

    try {
      assertTrue(service.submit(invocation("alice", "first")));
      assertTrue(firstEntered.await(1, TimeUnit.SECONDS));
      assertTrue(service.submit(invocation("bob", "second")));

      assertFalse(secondEntered.await(150, TimeUnit.MILLISECONDS));
      releaseFirst.countDown();
      assertTrue(secondEntered.await(1, TimeUnit.SECONDS));
      awaitListSize(flushed, 4);

      assertEquals(List.of("first", "second"), routed);
      assertEquals(4, flushed.size());
      assertTrue(flushed.get(0).contains("working on it"));
      assertTrue(flushed.get(1).contains("completed: first answer"));
      assertTrue(flushed.get(2).contains("working on it"));
      assertTrue(flushed.get(3).contains("completed: second answer"));
    } finally {
      releaseFirst.countDown();
      service.close();
    }
  }

  @Test
  void preservesWhisperAndRejectsDisabledOrClosedService() throws Exception {
    ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
    AgentServiceImpl disabled =
        new AgentServiceImpl(
            config(false, 1),
            invocation -> new AgentResult("id", "unused"),
            new OutService(queue),
            () -> {});

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
            new OutService(queue),
            () -> {});

    assertTrue(service.submit(invocation(false)));

    awaitQueueSize(queue, 2);
    assertTrue(queue.stream().anyMatch(reply -> reply.contains("could not answer")));
    service.close();
  }

  @Test
  void rejectsSubmissionAfterShutdown() throws Exception {
    ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
    AgentServiceImpl service =
        new AgentServiceImpl(
            config(true, 1),
            invocation -> new AgentResult("id", "unused"),
            new OutService(queue),
            () -> {});

    service.close();

    assertFalse(service.submit(invocation(false)));
    assertTrue(queue.take().contains("shutting down"));
  }

  @Test
  void coalescesPendingAmbientMessagesBehindAdmittedDirectWork() throws Exception {
    CountDownLatch firstAmbientEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstAmbient = new CountDownLatch(1);
    List<String> routed = new CopyOnWriteArrayList<>();
    AgentRouter router =
        invocation -> {
          routed.add(invocation.prompt());
          if ("first ambient".equals(invocation.prompt())) {
            firstAmbientEntered.countDown();
            try {
              releaseFirstAmbient.await();
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            }
          }
          return AgentResult.silent(invocation.requestId());
        };
    ArrayBlockingQueue<String> replies = new ArrayBlockingQueue<>(10);
    AgentServiceImpl service =
        new AgentServiceImpl(config(true, 1), router, new OutService(replies), () -> {});

    try {
      assertTrue(service.submit(invocation("alice", "first ambient", AgentInvocationMode.AMBIENT)));
      assertTrue(firstAmbientEntered.await(1, TimeUnit.SECONDS));
      assertTrue(service.submit(invocation("bob", "stale ambient", AgentInvocationMode.AMBIENT)));
      assertTrue(service.submit(invocation("alice", "direct", AgentInvocationMode.DIRECT)));
      assertTrue(service.submit(invocation("bob", "latest ambient", AgentInvocationMode.AMBIENT)));
      releaseFirstAmbient.countDown();

      awaitListSize(routed, 3);
      assertEquals(List.of("first ambient", "direct", "latest ambient"), routed);
      assertTrue(replies.stream().allMatch(reply -> reply.contains("agent ")));
    } finally {
      releaseFirstAmbient.countDown();
      service.close();
    }
  }

  @Test
  void silentResultsAndAmbientFailuresNeverEmitOrFlushReplies() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    AtomicInteger flushes = new AtomicInteger();
    AgentRouter router =
        invocation -> {
          if (calls.getAndIncrement() == 0) {
            return AgentResult.silent(invocation.requestId());
          }
          throw new IllegalStateException("ambient provider failed");
        };
    ArrayBlockingQueue<String> replies = new ArrayBlockingQueue<>(10);
    AgentServiceImpl service =
        new AgentServiceImpl(
            config(true, 1), router, new OutService(replies), flushes::incrementAndGet);

    try {
      assertTrue(service.submit(invocation("alice", "silent", AgentInvocationMode.AMBIENT)));
      while (calls.get() < 1) {
        Thread.sleep(5);
      }
      assertTrue(service.submit(invocation("alice", "failure", AgentInvocationMode.AMBIENT)));
      while (calls.get() < 2) {
        Thread.sleep(5);
      }
      Thread.sleep(20);

      assertTrue(replies.isEmpty());
      assertEquals(0, flushes.get());
    } finally {
      service.close();
    }
  }

  private AgentInvocation invocation(boolean whisper) {
    return new AgentInvocation(
        new AgentContext("programming", "alice", "trip-a", "hash-a", whisper, List.of("alice")),
        "question");
  }

  private AgentInvocation invocation(String nick, String prompt) {
    return invocation(nick, prompt, AgentInvocationMode.DIRECT);
  }

  private AgentInvocation invocation(String nick, String prompt, AgentInvocationMode mode) {
    return new AgentInvocation(
        new AgentContext(
            "programming", nick, "trip-" + nick, "hash-" + nick, false, List.of("alice", "bob")),
        prompt,
        mode);
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

  private static void awaitListSize(List<String> values, int expected) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (values.size() < expected && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
  }
}
