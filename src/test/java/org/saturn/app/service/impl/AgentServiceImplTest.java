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
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentInvocation;
import org.saturn.app.agent.api.AgentInvocationMode;
import org.saturn.app.agent.api.AgentResult;
import org.saturn.app.agent.api.AgentRouter;
import org.saturn.app.agent.config.AgentConfig;

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

    awaitQueueSize(queue, 2);
    assertTrue(queue.stream().anyMatch(value -> value.contains("busy")));
    assertTrue(queue.stream().anyMatch(value -> value.contains("@alice \nanswer")));
    service.close();
  }

  @Test
  void emitsOnlyTheFinalReplyForDirectRequests() throws Exception {
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

      awaitQueueContains(queue, "answer");
      List<String> messages = List.copyOf(queue);
      assertEquals(1, messages.size());
      assertTrue(messages.stream().anyMatch(message -> message.contains("answer")));
      assertTrue(messages.stream().noneMatch(message -> message.contains("request-1234")));
    } finally {
      service.close();
    }
  }

  @Test
  void doesNotFlushBeforeTheFinalReplyIsReady() throws Exception {
    CountDownLatch routerEntered = new CountDownLatch(1);
    CountDownLatch releaseRouter = new CountDownLatch(1);
    CountDownLatch finalReplyFlushed = new CountDownLatch(1);
    AtomicInteger flushes = new AtomicInteger();
    AgentServiceImpl service =
        new AgentServiceImpl(
            config(true, 1),
            invocation -> {
              routerEntered.countDown();
              try {
                releaseRouter.await();
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
              }
              return new AgentResult(invocation.requestId(), "answer");
            },
            new OutService(new ArrayBlockingQueue<>(10)),
            () -> {
              flushes.incrementAndGet();
              finalReplyFlushed.countDown();
            });

    try {
      assertTimeoutPreemptively(
          Duration.ofMillis(200), () -> assertTrue(service.submit(invocation(false))));
      assertTrue(routerEntered.await(1, TimeUnit.SECONDS));
      assertEquals(0, flushes.get());
      releaseRouter.countDown();
      assertTrue(finalReplyFlushed.await(1, TimeUnit.SECONDS));
      assertEquals(1, flushes.get());
    } finally {
      releaseRouter.countDown();
      service.close();
    }
  }

  @Test
  void postsOnlyOneFinalChatMessageWithoutRawUpdates() throws Exception {
    ArrayBlockingQueue<String> chats = new ArrayBlockingQueue<>(10);
    ArrayBlockingQueue<String> raw = new ArrayBlockingQueue<>(10);
    AgentInvocation invocation =
        new AgentInvocation(
            "request-123456789",
            new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of("alice")),
            "question");
    AgentServiceImpl service =
        new AgentServiceImpl(
            config(true, 1),
            request -> new AgentResult(request.requestId(), "answer"),
            new OutService(chats, raw),
            () -> {});

    try {
      assertTrue(service.submit(invocation));
      awaitQueueContains(chats, "@alice \nanswer");
      assertEquals(1, chats.size());
      assertEquals("@alice \nanswer", chats.peek());
      assertTrue(raw.isEmpty());
    } finally {
      service.close();
    }
  }

  @Test
  void postsOneStableFailureWhenRoutingFails() throws Exception {
    ArrayBlockingQueue<String> chats = new ArrayBlockingQueue<>(10);
    ArrayBlockingQueue<String> raw = new ArrayBlockingQueue<>(10);
    AgentInvocation invocation =
        new AgentInvocation(
            "request-failure-123",
            new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of("alice")),
            "question");
    AgentServiceImpl service =
        new AgentServiceImpl(
            config(true, 1),
            request -> {
              throw new IllegalStateException("provider detail");
            },
            new OutService(chats, raw),
            () -> {});

    try {
      assertTrue(service.submit(invocation));
      awaitQueueContains(chats, "could not answer");
      assertEquals(1, chats.size());
      assertTrue(raw.isEmpty());
    } finally {
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
      awaitListSize(flushed, 2);

      assertEquals(List.of("first", "second"), routed);
      assertEquals(2, flushed.size());
      assertTrue(flushed.get(0).contains("@alice \nfirst answer"));
      assertTrue(flushed.get(1).contains("@bob \nsecond answer"));
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

    awaitQueueSize(queue, 1);
    assertTrue(queue.stream().anyMatch(reply -> reply.contains("could not answer")));
    service.close();
  }

  @Test
  void continuesRoutingWhenAnOutboundReplyCannotBeQueued() throws Exception {
    AtomicInteger routed = new AtomicInteger();
    ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
    OutService outService =
        new OutService(queue) {
          private final AtomicInteger attempts = new AtomicInteger();

          @Override
          public String enqueueMessageForSending(String author, String message, boolean whisper) {
            if (attempts.getAndIncrement() == 0) {
              throw new IllegalStateException("queue unavailable");
            }
            return super.enqueueMessageForSending(author, message, whisper);
          }
        };
    AgentServiceImpl service =
        new AgentServiceImpl(
            config(true, 1),
            invocation -> {
              routed.incrementAndGet();
              return new AgentResult(invocation.requestId(), "answer");
            },
            outService,
            () -> {});

    try {
      assertTrue(service.submit(invocation(false)));
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (routed.get() < 1 && System.nanoTime() < deadline) {
        Thread.sleep(5);
      }
      assertEquals(1, routed.get());
    } finally {
      service.close();
    }
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
      assertTrue(replies.stream().noneMatch(reply -> reply.contains("[agent ")));
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

  private static void awaitQueueContains(ArrayBlockingQueue<String> queue, String expected)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (queue.stream().noneMatch(message -> message.contains(expected))
        && System.nanoTime() < deadline) {
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
