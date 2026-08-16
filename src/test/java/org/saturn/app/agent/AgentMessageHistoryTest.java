package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;

class AgentMessageHistoryTest {
  @Test
  void findsLatestMessageByRole() {
    List<LlmMessage> messages =
        List.of(
            LlmMessage.user("first"),
            LlmMessage.assistant("internal", List.of()),
            LlmMessage.user("latest"));

    assertEquals("latest", AgentMessageHistory.latestContent(messages, "user").orElseThrow());
  }

  @Test
  void ignoresInternalEvidenceWhenFindingLatestConversationAssistant() {
    List<LlmMessage> messages =
        List.of(
            LlmMessage.assistant("answer", List.of()),
            LlmMessage.system("[Internal tool evidence from room_users]\ndata"));

    assertTrue(
        AgentMessageHistory.latestConversationAssistant(messages).orElseThrow().equals("answer"));
  }
}
