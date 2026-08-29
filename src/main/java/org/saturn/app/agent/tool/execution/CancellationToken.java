package org.saturn.app.agent.tool.execution;

import java.util.concurrent.atomic.AtomicReference;

/** Monotonic request-local cancellation token. */
public final class CancellationToken {
  private final AtomicReference<AgentToolBatchContext.CancellationReason> reason =
      new AtomicReference<>(AgentToolBatchContext.CancellationReason.NONE);

  public void cancel() {
    reason.compareAndSet(
        AgentToolBatchContext.CancellationReason.NONE,
        AgentToolBatchContext.CancellationReason.EXPLICIT);
  }

  void cancelDeadline() {
    reason.set(AgentToolBatchContext.CancellationReason.DEADLINE);
  }

  public boolean isCancelled() {
    return reason.get() != AgentToolBatchContext.CancellationReason.NONE;
  }

  public AgentToolBatchContext.CancellationReason reason() {
    return reason.get();
  }
}
