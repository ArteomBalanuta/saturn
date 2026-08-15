package org.saturn.app.agent;

@FunctionalInterface
public interface AgentConversationContextProvider {
  String load(AgentContext context);

  static AgentConversationContextProvider none() {
    return ignored -> "";
  }
}
