package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmResponse;

class AgentTurnPolicyChainTest {
  @Test
  void appliesPoliciesInOrderAndCarriesEachResponseForward() throws Exception {
    List<String> order = new ArrayList<>();
    AgentTurnPolicy first =
        input -> {
          order.add("first:" + input.response().content());
          return new AgentTurnPolicyResult(new LlmResponse("middle", List.of(), "stop"), true);
        };
    AgentTurnPolicy second =
        input -> {
          order.add("second:" + input.response().content());
          return new AgentTurnPolicyResult(new LlmResponse("final", List.of(), "stop"), false);
        };
    AgentContext context =
        new AgentContext("programming", "alice", "trip", "hash", false, List.of("alice"));
    AgentTurnPolicyInput input =
        new AgentTurnPolicyInput(
            new LlmResponse("initial", List.of(), "stop"),
            new ArrayList<LlmMessage>(),
            new ArrayList<JsonObject>(),
            AgentCommandProseGuard.from(List.of()),
            new AgentTurnState(new AgentExecutionLimits(2, 2, Duration.ofSeconds(1))),
            "prompt",
            "correlation");

    AgentTurnPolicyResult result = new AgentTurnPolicyChain(List.of(first, second)).apply(input);

    assertEquals(List.of("first:initial", "second:middle"), order);
    assertEquals("final", result.response().content());
    assertTrue(result.correctionUsed());
  }

  @Test
  void stopsBeforeLaterPoliciesWhenPolicyBlocksFurtherEvaluation() throws Exception {
    List<String> order = new ArrayList<>();
    LlmResponse response = new LlmResponse("waiting for evidence", List.of(), "stop");
    AgentTurnPolicy gate =
        input -> {
          order.add("gate");
          return AgentTurnPolicyResult.stop(input.response());
        };
    AgentTurnPolicy later =
        input -> {
          order.add("later");
          return new AgentTurnPolicyResult(input.response(), false);
        };
    AgentTurnPolicyInput input =
        new AgentTurnPolicyInput(
            response,
            new ArrayList<LlmMessage>(),
            new ArrayList<JsonObject>(),
            AgentCommandProseGuard.from(List.of()),
            new AgentTurnState(new AgentExecutionLimits(2, 2, Duration.ofSeconds(1))),
            "prompt",
            "correlation");

    AgentTurnPolicyResult result = new AgentTurnPolicyChain(List.of(gate, later)).apply(input);

    assertEquals(List.of("gate"), order);
    assertEquals(response, result.response());
    assertFalse(result.continuePolicyEvaluation());
  }
}
