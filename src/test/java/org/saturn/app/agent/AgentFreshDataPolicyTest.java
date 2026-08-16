package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.llm.LlmToolCall;

class AgentFreshDataPolicyTest {
  private final AgentFreshDataPolicy policy = new AgentFreshDataPolicy();

  @Test
  void acceptsHistorySynthesisOnlyAfterHistoryEvidence() {
    LlmResponse response = new LlmResponse("Profile", List.of(), "stop");
    assertFalse(policy.satisfiesProfileContract(response, List.of()));
    assertTrue(
        policy.satisfiesProfileContract(
            response, List.of(AgentToolResult.success("user_message_history", "{}"))));
  }

  @Test
  void matchesExpectedHistoryTarget() {
    LlmToolCall call = new LlmToolCall("id", "user_message_history", "{\"nick\":\"Jill\"}");
    assertTrue(policy.matchesTarget(call, Optional.of("jill")));
    assertFalse(policy.matchesTarget(call, Optional.of("nex")));
  }
}
