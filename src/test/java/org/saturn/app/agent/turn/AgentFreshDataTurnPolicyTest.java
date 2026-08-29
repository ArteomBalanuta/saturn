package org.saturn.app.agent.turn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentExecutionLimits;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.routing.AgentCommandProseGuard;

class AgentFreshDataTurnPolicyTest {
  @Test
  void continuesOnlyWhenRequiredFreshToolEvidenceIsAvailable() throws Exception {
    AgentFreshDataTurnPolicy policy = new AgentFreshDataTurnPolicy();
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(2, 2, Duration.ofSeconds(1)));

    assertTrue(policy.apply(input(Optional.empty(), state)).continuePolicyEvaluation());
    assertFalse(
        policy
            .apply(input(Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY), state))
            .continuePolicyEvaluation());

    state.recordSuccessfulTool(AgentFreshnessPolicy.USER_MESSAGE_HISTORY);

    assertTrue(
        policy
            .apply(input(Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY), state))
            .continuePolicyEvaluation());
  }

  private static AgentTurnPolicyInput input(
      Optional<String> requiredFreshTool, AgentTurnState state) {
    return new AgentTurnPolicyInput(
        new LlmResponse("response", List.of(), "stop"),
        new ArrayList<LlmMessage>(),
        new ArrayList<JsonObject>(),
        AgentCommandProseGuard.from(List.of()),
        state,
        "prompt",
        "correlation",
        requiredFreshTool);
  }
}
