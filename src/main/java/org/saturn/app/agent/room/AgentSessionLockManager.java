package org.saturn.app.agent.room;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import org.saturn.app.agent.api.AgentRoutingException;

/** Owns fair striped locks used to serialize requests sharing an agent memory key. */
public final class AgentSessionLockManager {
  private final ReentrantLock[] locks;

  /** Implements the {@code AgentSessionLockManager} operation for this agent component. */
  public AgentSessionLockManager() {
    locks = new ReentrantLock[64];
    Arrays.setAll(locks, ignored -> new ReentrantLock(true));
  }

  /**
   * Implements the {@code withLock} operation for this agent component.
   *
   * @param memoryKey input argument used by this operation
   * @param operation input argument used by this operation
   * @return the operation result
   */
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
