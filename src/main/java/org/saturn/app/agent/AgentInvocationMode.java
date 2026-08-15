package org.saturn.app.agent;

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
