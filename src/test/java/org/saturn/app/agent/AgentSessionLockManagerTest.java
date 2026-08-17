package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AgentSessionLockManagerTest {
  @Test
  void serializesOperationsForTheSameMemoryKey() throws Exception {
    AgentSessionLockManager manager = new AgentSessionLockManager();
    CountDownLatch firstEntered = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicBoolean secondEntered = new AtomicBoolean();

    Thread first =
        Thread.startVirtualThread(
            () -> {
              try {
                manager.withLock(
                    "same-key",
                    () -> {
                      firstEntered.countDown();
                      try {
                        releaseFirst.await();
                      } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                      }
                      return null;
                    });
              } catch (AgentRoutingException exception) {
                throw new AssertionError(exception);
              }
            });
    assertTrue(firstEntered.await(1, TimeUnit.SECONDS));

    Thread second =
        Thread.startVirtualThread(
            () -> {
              try {
                manager.withLock(
                    "same-key",
                    () -> {
                      secondEntered.set(true);
                      return null;
                    });
              } catch (AgentRoutingException exception) {
                throw new AssertionError(exception);
              }
            });

    assertFalse(second.join(Duration.ofMillis(100)));
    assertFalse(secondEntered.get());
    releaseFirst.countDown();
    first.join();
    second.join();
    assertTrue(secondEntered.get());
  }

  @Test
  void returnsTheOperationResult() throws Exception {
    AgentSessionLockManager manager = new AgentSessionLockManager();

    assertEquals("answer", manager.withLock("key", () -> "answer"));
  }
}
