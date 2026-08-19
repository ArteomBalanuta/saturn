package org.saturn.app.agent.api;

import java.util.Objects;
import java.util.UUID;

/** Represents a requested agent invocation, including its room, user, and prompt context. */
public record AgentInvocation(
    String requestId,
    AgentContext context,
    String prompt,
    AgentInvocationMode mode,
    String currentMessageText,
    boolean commandOriginated) {
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
    this(requestId, context, prompt, AgentInvocationMode.DIRECT, null, false);
  }

  public AgentInvocation(
      String requestId, AgentContext context, String prompt, AgentInvocationMode mode) {
    this(requestId, context, prompt, mode, null, false);
  }

  public AgentInvocation(
      String requestId,
      AgentContext context,
      String prompt,
      AgentInvocationMode mode,
      String currentMessageText) {
    this(requestId, context, prompt, mode, currentMessageText, false);
  }

  public AgentInvocation(AgentContext context, String prompt) {
    this(UUID.randomUUID().toString(), context, prompt, AgentInvocationMode.DIRECT, null, false);
  }

  public AgentInvocation(AgentContext context, String prompt, AgentInvocationMode mode) {
    this(UUID.randomUUID().toString(), context, prompt, mode, null, false);
  }
}
