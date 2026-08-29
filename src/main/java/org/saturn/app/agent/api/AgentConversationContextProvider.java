package org.saturn.app.agent.api;

@FunctionalInterface
/** Provides conversation context for an agent invocation. */
public interface AgentConversationContextProvider {
  String load(AgentContext context);

  /**
   * Implements the {@code load} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @param author input argument used by this operation
   * @param text input argument used by this operation
   * @return the operation result
   */
  default String load(AgentContext context, String author, String text) {
    return load(context);
  }

  /**
   * Implements the {@code none} operation for this agent component.
   *
   * @return the operation result
   */
  static AgentConversationContextProvider none() {
    return ignored -> "";
  }
}
