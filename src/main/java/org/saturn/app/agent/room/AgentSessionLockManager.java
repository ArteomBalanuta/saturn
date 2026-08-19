package org.saturn.app.agent.room;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import org.saturn.app.agent.api.AgentRoutingException;

/** Owns fair striped locks used to serialize requests sharing an agent memory key. */
public final class AgentSessionLockManager {
  private final ReentrantLock[] locks;

  public AgentSessionLockManager() {
    locks = new ReentrantLock[64];
    Arrays.setAll(locks, ignored -> new ReentrantLock(true));
  }

  public <T> T withLock(String memoryKey, LockedOperation<T> operation)
      throws AgentRoutingException {
    ReentrantLock lock = locks[Math.floorMod(memoryKey.hashCode(), locks.length)];
    lock.lock();
    try {
      return operation.run();
    } finally {
      lock.unlock();
    }
  }

  @FunctionalInterface
  /** Defines the operation used to locked operation. */
  /** Defines the operation used to locked operation. */
  public interface LockedOperation<T> {
    T run() throws AgentRoutingException;
  }
}
