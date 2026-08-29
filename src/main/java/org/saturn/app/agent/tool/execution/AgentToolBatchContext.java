package org.saturn.app.agent.tool.execution;

import java.time.Instant;
import java.util.Objects;

/**
 * Request-local deadline and cancellation authority for one tool-call batch.
 *
 * @param deadline instant after which work must be treated as expired
 * @param cancellation token shared by the batch's tool executions
 * @throws NullPointerException if {@code deadline} or {@code cancellation} is {@code null}
 */
public record AgentToolBatchContext(Instant deadline, CancellationToken cancellation) {
  /**
   * Defines the enum {@code CancellationReason} in the Saturn agent runtime.
   *
   * <p>This type is part of the source-compatible agent boundary; validation and failure behavior
   * are retained by its implementation.
   */
  /** Reasons a tool batch can be considered cancelled. */
  public enum CancellationReason {
    /** No cancellation or deadline expiry has been observed. */
    NONE,
    /** Cancellation was explicitly requested by the owning turn. */
    EXPLICIT,
    /** The batch deadline has elapsed. */
    DEADLINE
  }

  /**
   * Validates and stores a batch deadline and cancellation token.
   *
   * @throws NullPointerException if either record component is {@code null}
   */
  public AgentToolBatchContext {
    deadline = Objects.requireNonNull(deadline, "deadline");
    cancellation = Objects.requireNonNull(cancellation, "cancellation");
  }

  /**
   * Implements the {@code unlimited} operation for this agent component.
   *
   * @return the operation result
   */
  public static AgentToolBatchContext unlimited() {
    return new AgentToolBatchContext(Instant.MAX, new CancellationToken());
  }

  /**
   * Implements the {@code expired} operation for this agent component.
   *
   * @return the operation result
   */
  public boolean expired() {
    return !Instant.now().isBefore(deadline);
  }

  /**
   * Implements the {@code cancellationReason} operation for this agent component.
   *
   * @return the operation result
   */
  public CancellationReason cancellationReason() {
    if (cancellation.reason() != CancellationReason.NONE) {
      return cancellation.reason();
    }
    return expired() ? CancellationReason.DEADLINE : CancellationReason.NONE;
  }
}
