package org.saturn.app.agent.api;

import java.util.Objects;

/** Represents the outcome returned by an agent invocation. */
public record AgentResult(String correlationId, String content, boolean shouldReply) {
  public AgentResult {
    if (correlationId == null || correlationId.isBlank()) {
      throw new IllegalArgumentException("correlationId must not be blank");
    }
    Objects.requireNonNull(content, "content");
  }

  /**
   * Implements the {@code AgentResult} operation for this agent component.
   *
   * @param correlationId input argument used by this operation
   * @param content input argument used by this operation
   */
  public AgentResult(String correlationId, String content) {
    this(correlationId, content, true);
  }

  /**
   * Implements the {@code reply} operation for this agent component.
   *
   * @param correlationId input argument used by this operation
   * @param content input argument used by this operation
   * @return the operation result
   */
  public static AgentResult reply(String correlationId, String content) {
    return new AgentResult(correlationId, content, true);
  }

  /**
   * Implements the {@code silent} operation for this agent component.
   *
   * @param correlationId input argument used by this operation
   * @return the operation result
   */
  public static AgentResult silent(String correlationId) {
    return new AgentResult(correlationId, "", false);
  }
}
