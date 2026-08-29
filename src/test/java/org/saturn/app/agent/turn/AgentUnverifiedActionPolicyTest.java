package org.saturn.app.agent.turn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentExecutionLimits;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.routing.AgentCommandProseGuard;
import org.saturn.app.agent.routing.AgentResponseCorrector;

class AgentUnverifiedActionPolicyTest {
  @Test
  void correctsAnUnverifiedActionBeforeTheNextPolicy() throws Exception {
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(2, 2, Duration.ofSeconds(1)));
    AgentUnverifiedActionPolicy policy =
        new AgentUnverifiedActionPolicy(
            new AgentResponseCorrector(
                request -> new LlmResponse("The action is complete.", List.of(), "stop")));
    AgentTurnPolicyInput input =
        input(state, new LlmResponse("I will fetch the weather.", List.of(), "stop"));

    AgentTurnPolicyResult result = policy.apply(input);

    assertEquals("The action is complete.", result.response().content());
    assertTrue(state.unverifiedActionChecked());
    assertTrue(input.messages().size() >= 2);
  }

  @Test
  void leavesAResponseUntouchedAfterTheCheckWasCompleted() throws Exception {
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(2, 2, Duration.ofSeconds(1)));
    state.markUnverifiedActionChecked();
    AgentUnverifiedActionPolicy policy =
        new AgentUnverifiedActionPolicy(
            new AgentResponseCorrector(
                request -> {
                  throw new AssertionError("checked responses must not be corrected");
                }));
    LlmResponse response = new LlmResponse("I will fetch the weather.", List.of(), "stop");

    AgentTurnPolicyResult result = policy.apply(input(state, response));

    assertEquals(response, result.response());
  }

  private static AgentTurnPolicyInput input(AgentTurnState state, LlmResponse response) {
    return new AgentTurnPolicyInput(
        response,
        new ArrayList<>(),
        List.of(),
        AgentCommandProseGuard.from(List.of()),
        state,
        "prompt",
        "correlation");
  }
}
