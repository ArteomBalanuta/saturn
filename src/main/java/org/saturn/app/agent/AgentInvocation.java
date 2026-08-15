package org.saturn.app.agent;

import java.util.Objects;
import java.util.UUID;

public record AgentInvocation(
    String requestId,
    AgentContext context,
    String prompt,
    AgentInvocationMode mode,
    String currentMessageText) {
  public AgentInvocation {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId must not be blank");
    }
    Objects.requireNonNull(context, "context");
    if (prompt == null || prompt.isBlank()) {
      throw new IllegalArgumentException("prompt must not be blank");
    }
    Objects.requireNonNull(mode, "mode");
  }

  public AgentInvocation(String requestId, AgentContext context, String prompt) {
    this(requestId, context, prompt, AgentInvocationMode.DIRECT, null);
  }

  public AgentInvocation(
      String requestId, AgentContext context, String prompt, AgentInvocationMode mode) {
    this(requestId, context, prompt, mode, null);
  }

  public AgentInvocation(AgentContext context, String prompt) {
    this(UUID.randomUUID().toString(), context, prompt, AgentInvocationMode.DIRECT, null);
  }

  public AgentInvocation(AgentContext context, String prompt, AgentInvocationMode mode) {
    this(UUID.randomUUID().toString(), context, prompt, mode, null);
  }
}
