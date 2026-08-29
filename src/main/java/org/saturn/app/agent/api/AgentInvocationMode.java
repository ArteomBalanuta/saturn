package org.saturn.app.agent.api;

/** Defines the modes in which an agent invocation may be performed. */
public enum AgentInvocationMode {
  DIRECT(true),
  MENTION(true),
  AMBIENT(false),
  MODERATION(false);

  private final boolean requiresReply;

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param requiresReply the requiresReply input; null handling follows the validation performed by
   *     this declaration
   */
  AgentInvocationMode(boolean requiresReply) {
    this.requiresReply = requiresReply;
  }

  /**
   * Implements the {@code requiresReply} operation for this agent component.
   *
   * @return the operation result
   */
  public boolean requiresReply() {
    return requiresReply;
  }
}
