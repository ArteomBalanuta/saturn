package org.saturn.app.agent;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/** Owns fair striped locks used to serialize requests sharing an agent memory key. */
final class AgentSessionLockManager {
  private final ReentrantLock[] locks;

  AgentSessionLockManager() {
    locks = new ReentrantLock[64];
    Arrays.setAll(locks, ignored -> new ReentrantLock(true));
  }

  <T> T withLock(String memoryKey, LockedOperation<T> operation) throws AgentRoutingException {
    ReentrantLock lock = locks[Math.floorMod(memoryKey.hashCode(), locks.length)];
    lock.lock();
    try {
      return operation.run();
    } finally {
      lock.unlock();
    }
  }

  @FunctionalInterface
  interface LockedOperation<T> {
    T run() throws AgentRoutingException;
  }
}
