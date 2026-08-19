package org.saturn.app.agent.api;

/** Defines the modes in which an agent invocation may be performed. */
public enum AgentInvocationMode {
  DIRECT(true),
  MENTION(true),
  AMBIENT(false),
  MODERATION(false);

  private final boolean requiresReply;

  AgentInvocationMode(boolean requiresReply) {
    this.requiresReply = requiresReply;
  }

  public boolean requiresReply() {
    return requiresReply;
  }
}
