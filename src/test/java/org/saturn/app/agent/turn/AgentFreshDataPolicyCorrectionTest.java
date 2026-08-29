package org.saturn.app.agent.turn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.llm.LlmResponse;

class AgentFreshDataPolicyCorrectionTest {
  @Test
  void requiresCorrectionWhenHistoryEvidenceIsMissing() {
    AgentFreshDataPolicy policy = new AgentFreshDataPolicy();
    LlmResponse response = new LlmResponse("profile", List.of(), "stop");

    assertTrue(
        policy.requiresSynthesisCorrection(
            Optional.of("user_message_history"), response, List.of()));
  }

  @Test
  void doesNotRequireCorrectionAfterHistoryEvidenceIsPresent() {
    AgentFreshDataPolicy policy = new AgentFreshDataPolicy();
    LlmResponse response = new LlmResponse("profile", List.of(), "stop");
    AgentToolResult history = AgentToolResult.success("user_message_history", "{\"messages\":[]}");

    assertFalse(
        policy.requiresSynthesisCorrection(
            Optional.of("user_message_history"), response, List.of(history)));
  }

  @Test
  void requiresFinalValidationEvenForAFailurePlaceholder() {
    AgentFreshDataPolicy policy = new AgentFreshDataPolicy();
    LlmResponse response =
        new LlmResponse("The agent could not answer that request.", List.of(), "stop");

    assertTrue(
        policy.requiresFinalSynthesisValidation(
            Optional.of("user_message_history"), response, List.of()));
  }
}
