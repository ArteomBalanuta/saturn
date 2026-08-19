package org.saturn.app.agent.api;

import java.util.List;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.agent.llm.LlmMessage;

/** Provides persistent memory operations for agent conversations. */
public interface AgentMemoryStore {
  List<LlmMessage> load(AgentContext context, AgentConfig config);

  void append(
      AgentContext context, String userContent, String assistantContent, AgentConfig config);

  default void appendToolEvidence(
      AgentContext context, String toolName, String content, AgentConfig config) {}

  static AgentMemoryStore none() {
    return new AgentMemoryStore() {
      @Override
      public List<LlmMessage> load(AgentContext context, AgentConfig config) {
        return List.of();
      }

      @Override
      public void append(
          AgentContext context, String userContent, String assistantContent, AgentConfig config) {}
    };
  }
}
