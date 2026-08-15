package org.saturn.app.agent;

@FunctionalInterface
public interface AgentConversationContextProvider {
  String load(AgentContext context);

  default String load(AgentContext context, String author, String text) {
    return load(context);
  }

  static AgentConversationContextProvider none() {
    return ignored -> "";
  }
}
