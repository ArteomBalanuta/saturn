package org.saturn.app.agent;

import java.util.Objects;

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
