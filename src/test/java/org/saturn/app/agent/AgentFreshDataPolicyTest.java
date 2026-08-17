package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.llm.LlmToolCall;

class AgentFreshDataPolicyTest {
  @Test
  void identifiesAndRejectsARepeatedAssistantSynthesis() {
    List<LlmMessage> history = List.of(LlmMessage.assistant("previous answer", List.of()));
    LlmResponse response = new LlmResponse(" previous answer ", List.of(), "stop");

    assertEquals(true, new AgentFreshDataPolicy().repeatsPreviousAssistant(response, history));
    assertThrows(
        AgentRoutingException.class,
        () -> new AgentFreshDataPolicy().requireFreshSynthesis(response, history));
  }

  @Test
  void acceptsAFreshSynthesisWithoutToolCalls() throws Exception {
    LlmResponse response = new LlmResponse("new answer", List.of(), "stop");

    assertEquals(response, new AgentFreshDataPolicy().requireFreshSynthesis(response, List.of()));
  }

  @Test
  void rejectsToolCallsDuringFreshSynthesis() {
    LlmResponse response =
        new LlmResponse(
            "checking", List.of(new LlmToolCall("call-1", "room_users", "{}")), "tool_calls");

    assertThrows(
        AgentRoutingException.class,
        () -> new AgentFreshDataPolicy().requireFreshSynthesis(response, List.of()));
  }

  @Test
  void recognizesOnlyTheExactFreshToolCallAndTarget() {
    AgentFreshDataPolicy policy = new AgentFreshDataPolicy();
    LlmResponse response =
        new LlmResponse(
            "checking",
            List.of(new LlmToolCall("history-1", "user_message_history", "{\"nick\":\"Jill\"}")),
            "tool_calls");

    assertTrue(
        policy.isExactToolCall(response, "user_message_history", java.util.Optional.of("jill")));
    assertFalse(
        policy.isExactToolCall(response, "user_message_history", java.util.Optional.of("nex")));
  }

  @Test
  void rejectsMalformedArgumentsAndMultipleFreshToolCalls() {
    AgentFreshDataPolicy policy = new AgentFreshDataPolicy();
    LlmResponse malformed =
        new LlmResponse(
            "checking",
            List.of(new LlmToolCall("history-1", "user_message_history", "not-json")),
            "tool_calls");
    LlmResponse multiple =
        new LlmResponse(
            "checking",
            List.of(
                new LlmToolCall("history-1", "user_message_history", "{\"nick\":\"jill\"}"),
                new LlmToolCall("history-2", "user_message_history", "{\"nick\":\"jill\"}")),
            "tool_calls");

    assertFalse(
        policy.isExactToolCall(malformed, "user_message_history", java.util.Optional.of("jill")));
    assertFalse(
        policy.isExactToolCall(multiple, "user_message_history", java.util.Optional.of("jill")));
  }

  @Test
  void rejectsNonObjectFreshHistoryArgumentsWithoutThrowing() {
    AgentFreshDataPolicy policy = new AgentFreshDataPolicy();
    java.util.Optional<String> expectedNick = java.util.Optional.of("alice");

    for (String arguments : List.of("null", "[]", "{\"nick\":null}", "{\"nick\":{}}")) {
      assertFalse(
          policy.matchesTarget(
              new LlmToolCall("call", AgentFreshnessPolicy.USER_MESSAGE_HISTORY, arguments),
              expectedNick),
          arguments);
    }
  }

  @Test
  void rejectsNullResponsesWithoutLeakingNullPointerExceptions() {
    AgentFreshDataPolicy policy = new AgentFreshDataPolicy();

    assertFalse(policy.satisfiesProfileContract(null, List.of()));
    assertFalse(
        policy.requiresSynthesisCorrection(
            java.util.Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY), null, List.of()));
    assertFalse(
        policy.requiresFinalSynthesisValidation(
            java.util.Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY), null, List.of()));
    assertFalse(
        policy.repeatsPreviousAssistant(
            null, List.of(LlmMessage.assistant("previous answer", List.of()))));
    assertThrows(
        AgentRoutingException.class,
        () -> policy.requireExactToolCall(null, "room_users", java.util.Optional.empty()));
    assertThrows(AgentRoutingException.class, () -> policy.requireFreshSynthesis(null, List.of()));
  }
}
