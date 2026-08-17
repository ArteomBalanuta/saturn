package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmResponse;

class AgentFreshDataCoordinatorTest {
  @Test
  void passesThroughResponsesWhenNoFreshToolIsRequired() throws Exception {
    AgentFreshDataCoordinator coordinator =
        new AgentFreshDataCoordinator(
            request -> new LlmResponse("unused", List.of(), "stop"), new AgentFreshDataPolicy());
    LlmResponse response = new LlmResponse("ordinary answer", List.of(), "stop");
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(3, 2, java.time.Duration.ofSeconds(1)));

    AgentFreshDataCoordinator.Result result =
        coordinator.process(
            response,
            new java.util.ArrayList<>(),
            List.<JsonObject>of(),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            new AgentContext("room", "nick", null, null, false, List.of()),
            null,
            state,
            "correlation",
            (context, call, toolResult) -> toolResult.content(),
            (definitions, toolName) -> definitions);

    assertEquals(response, result.response());
    assertFalse(result.restartLoop());
  }

  @Test
  void finalValidationRejectsMissingFreshEvidence() {
    AgentFreshDataCoordinator coordinator =
        new AgentFreshDataCoordinator(
            request -> new LlmResponse("unused", List.of(), "stop"), new AgentFreshDataPolicy());

    assertThrows(
        AgentRoutingException.class,
        () ->
            coordinator.validateFinal(
                Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY),
                new LlmResponse("answer", List.of(), "stop"),
                List.of()));
  }

  @Test
  void finalValidationAcceptsFreshHistoryEvidence() throws Exception {
    AgentFreshDataCoordinator coordinator =
        new AgentFreshDataCoordinator(
            request -> new LlmResponse("unused", List.of(), "stop"), new AgentFreshDataPolicy());

    coordinator.validateFinal(
        Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY),
        new LlmResponse("answer", List.of(), "stop"),
        List.of(AgentToolResult.success(AgentFreshnessPolicy.USER_MESSAGE_HISTORY, "{}")));
  }

  @Test
  void rejectsMissingFreshToolWhenToolCallsAreDisabled() {
    AgentFreshDataCoordinator coordinator =
        new AgentFreshDataCoordinator(
            request -> new LlmResponse("unused", List.of(), "stop"), new AgentFreshDataPolicy());
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(3, 2, java.time.Duration.ofSeconds(1)));
    state.disableTools();

    assertThrows(
        AgentRoutingException.class,
        () ->
            coordinator.process(
                new LlmResponse("I can answer from memory.", List.of(), "stop"),
                new java.util.ArrayList<>(),
                List.<JsonObject>of(),
                List.of(),
                Optional.of("room_users"),
                Optional.empty(),
                new AgentContext("room", "nick", null, null, false, List.of()),
                null,
                state,
                "correlation",
                (context, call, toolResult) -> toolResult.content(),
                (definitions, toolName) -> definitions));
  }

  @Test
  void rejectsNullFreshToolCorrectionWithStableRoutingError() {
    AgentFreshDataCoordinator coordinator =
        new AgentFreshDataCoordinator(request -> null, new AgentFreshDataPolicy());
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(3, 2, java.time.Duration.ofSeconds(1)));

    assertThrows(
        AgentRoutingException.class,
        () ->
            coordinator.process(
                new LlmResponse("I can answer from memory.", List.of(), "stop"),
                new java.util.ArrayList<>(),
                List.<JsonObject>of(),
                List.of(),
                Optional.of("room_users"),
                Optional.empty(),
                new AgentContext("room", "nick", null, null, false, List.of()),
                null,
                state,
                "correlation",
                (context, call, toolResult) -> toolResult.content(),
                (definitions, toolName) -> definitions));
  }
}
