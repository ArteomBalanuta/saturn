package org.saturn.app.agent.turn;

import java.util.List;
import java.util.Optional;
import org.saturn.app.agent.llm.LlmMessage;

/** Shared queries for persisted and in-flight conversation messages. */
public final class AgentMessageHistory {
  private static final String ASSISTANT_ROLE = "assistant";
  private static final String INTERNAL_TOOL_EVIDENCE_PREFIX = "[Internal tool evidence from ";
  private static final String INTERNAL_TOOL_EVIDENCE_SUFFIX = "]\n";

  private AgentMessageHistory() {}

  public static Optional<String> latestContent(List<LlmMessage> messages, String role) {
    for (int index = messages.size() - 1; index >= 0; index--) {
      LlmMessage message = messages.get(index);
      if (role.equals(message.role())) {
        return Optional.ofNullable(message.content());
      }
    }
    return Optional.empty();
  }

  public static Optional<String> latestConversationAssistant(List<LlmMessage> messages) {
    for (int index = messages.size() - 1; index >= 0; index--) {
      LlmMessage message = messages.get(index);
      if (ASSISTANT_ROLE.equals(message.role()) && !isInternalToolEvidence(message.content())) {
        return Optional.ofNullable(message.content());
      }
    }
    return Optional.empty();
  }

  public static Optional<String> internalToolEvidenceName(String content) {
    if (content == null || !content.startsWith(INTERNAL_TOOL_EVIDENCE_PREFIX)) {
      return Optional.empty();
    }
    int end =
        content.indexOf(INTERNAL_TOOL_EVIDENCE_SUFFIX, INTERNAL_TOOL_EVIDENCE_PREFIX.length());
    if (end <= INTERNAL_TOOL_EVIDENCE_PREFIX.length()) {
      return Optional.empty();
    }
    return Optional.of(content.substring(INTERNAL_TOOL_EVIDENCE_PREFIX.length(), end));
  }

  public static boolean isInternalToolEvidence(String content) {
    return content != null && content.startsWith(INTERNAL_TOOL_EVIDENCE_PREFIX);
  }
}
