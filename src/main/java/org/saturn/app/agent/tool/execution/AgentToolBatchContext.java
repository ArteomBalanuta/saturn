package org.saturn.app.agent.tool.execution;

import java.time.Instant;
import java.util.Objects;

/** Request-local deadline and cancellation authority. */
public record AgentToolBatchContext(Instant deadline, CancellationToken cancellation) {
  public enum CancellationReason {
    NONE,
    EXPLICIT,
    DEADLINE
  }

  public AgentToolBatchContext {
    deadline = Objects.requireNonNull(deadline, "deadline");
    cancellation = Objects.requireNonNull(cancellation, "cancellation");
  }

  public static AgentToolBatchContext unlimited() {
    return new AgentToolBatchContext(Instant.MAX, new CancellationToken());
  }

  public boolean expired() {
    return !Instant.now().isBefore(deadline);
  }

  public CancellationReason cancellationReason() {
    if (cancellation.reason() != CancellationReason.NONE) {
      return cancellation.reason();
    }
    return expired() ? CancellationReason.DEADLINE : CancellationReason.NONE;
  }
}
