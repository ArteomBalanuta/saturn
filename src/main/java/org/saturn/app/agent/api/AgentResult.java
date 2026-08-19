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

  public AgentResult(String correlationId, String content) {
    this(correlationId, content, true);
  }

  public static AgentResult reply(String correlationId, String content) {
    return new AgentResult(correlationId, content, true);
  }

  public static AgentResult silent(String correlationId) {
    return new AgentResult(correlationId, "", false);
  }
}
