package org.saturn.app.agent.api;

import java.util.List;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.agent.llm.LlmMessage;

/** Provides persistent memory operations for agent conversations. */
public interface AgentMemoryStore {
  List<LlmMessage> load(AgentContext context, AgentConfig config);

  void append(
      AgentContext context, String userContent, String assistantContent, AgentConfig config);

  /**
   * Implements the {@code appendToolEvidence} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @param toolName input argument used by this operation
   * @param content input argument used by this operation
   * @param config input argument used by this operation
   */
  default void appendToolEvidence(
      AgentContext context, String toolName, String content, AgentConfig config) {}

  /**
   * Implements the {@code none} operation for this agent component.
   *
   * @return the operation result
   */
  static AgentMemoryStore none() {
    return new AgentMemoryStore() {
      /**
       * Implements the {@code load} operation for this agent component.
       *
       * @param context input argument used by this operation
       * @param config input argument used by this operation
       * @return the operation result
       */
      @Override
      public List<LlmMessage> load(AgentContext context, AgentConfig config) {
        return List.of();
      }

      /**
       * Implements the {@code append} operation for this agent component.
       *
       * @param context input argument used by this operation
       * @param userContent input argument used by this operation
       * @param assistantContent input argument used by this operation
       * @param config input argument used by this operation
       */
      @Override
      public void append(
          AgentContext context, String userContent, String assistantContent, AgentConfig config) {}
    };
  }
}
