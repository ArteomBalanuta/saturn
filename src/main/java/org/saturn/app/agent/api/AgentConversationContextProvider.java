package org.saturn.app.agent.api;

@FunctionalInterface
/** Provides conversation context for an agent invocation. */
public interface AgentConversationContextProvider {
  String load(AgentContext context);

  default String load(AgentContext context, String author, String text) {
    return load(context);
  }

  static AgentConversationContextProvider none() {
    return ignored -> "";
  }
}
