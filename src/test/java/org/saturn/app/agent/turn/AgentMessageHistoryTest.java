package org.saturn.app.agent.turn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

  @Test
  void returnsEmptyWhenNoMessageMatchesOrContentIsNull() {
    List<LlmMessage> messages =
        List.of(new LlmMessage("assistant", null, List.of(), null), LlmMessage.tool("id", "tool"));

    assertTrue(AgentMessageHistory.latestContent(messages, "user").isEmpty());
    assertTrue(AgentMessageHistory.latestConversationAssistant(messages).isEmpty());
    assertFalse(AgentMessageHistory.latestContent(List.of(), "assistant").isPresent());
  }

  @Test
  void latestContentUsesOnlyTheRequestedRoleAndPreservesContentExactly() {
    List<LlmMessage> messages =
        List.of(
            LlmMessage.user(" earlier "),
            LlmMessage.assistant("answer", List.of()),
            LlmMessage.user("  latest with spaces  "));

    assertEquals(
        "  latest with spaces  ",
        AgentMessageHistory.latestContent(messages, "user").orElseThrow());
    assertEquals("answer", AgentMessageHistory.latestContent(messages, "assistant").orElseThrow());
  }
}
