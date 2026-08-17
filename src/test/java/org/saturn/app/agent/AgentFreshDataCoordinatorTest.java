package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  void rejectsFreshHistoryWhenTheToolCallBudgetIsAlreadyConsumed() {
    AgentFreshDataCoordinator coordinator =
        new AgentFreshDataCoordinator(
            request -> new LlmResponse("unused", List.of(), "stop"), new AgentFreshDataPolicy());
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(3, 1, java.time.Duration.ofSeconds(1)));
    assertTrue(state.reserveToolCalls(1));

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () ->
                coordinator.process(
                    new LlmResponse("I can answer from memory.", List.of(), "stop"),
                    new java.util.ArrayList<>(),
                    List.<JsonObject>of(),
                    List.of(),
                    Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY),
                    Optional.of("alice"),
                    new AgentContext("room", "nick", null, null, false, List.of()),
                    null,
                    state,
                    "correlation",
                    (context, call, toolResult) -> toolResult.content(),
                    (definitions, toolName) -> definitions));

    assertEquals("Agent tool-call limit reached before loading fresh data", exception.getMessage());
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

  @Test
  void rejectsASecondMissingRequiredToolCorrection() throws Exception {
    AgentFreshDataCoordinator coordinator =
        new AgentFreshDataCoordinator(
            request ->
                new LlmResponse(
                    "",
                    List.of(new org.saturn.app.agent.llm.LlmToolCall("call-1", "room_users", "{}")),
                    "tool_calls"),
            new AgentFreshDataPolicy());
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(3, 2, java.time.Duration.ofSeconds(1)));
    AgentContext context = new AgentContext("room", "nick", null, null, false, List.of());

    coordinator.process(
        new LlmResponse("I can answer from memory.", List.of(), "stop"),
        new java.util.ArrayList<>(),
        List.<JsonObject>of(),
        List.of(),
        Optional.of("room_users"),
        Optional.empty(),
        context,
        null,
        state,
        "correlation",
        (ignoredContext, ignoredCall, ignoredResult) -> "{}",
        (definitions, toolName) -> definitions);
    assertTrue(state.freshnessCorrectionUsed());

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () ->
                coordinator.process(
                    new LlmResponse("I can answer from memory again.", List.of(), "stop"),
                    new java.util.ArrayList<>(),
                    List.<JsonObject>of(),
                    List.of(),
                    Optional.of("room_users"),
                    Optional.empty(),
                    context,
                    null,
                    state,
                    "correlation",
                    (ignoredContext, ignoredCall, ignoredResult) -> "{}",
                    (definitions, toolName) -> definitions));

    assertEquals("Agent did not call the required fresh-data tool", exception.getMessage());
  }

  @Test
  void rejectsSynthesisWhenTheCorrectionWasAlreadyUsed() {
    AgentFreshDataCoordinator coordinator =
        new AgentFreshDataCoordinator(
            request -> new LlmResponse("unused", List.of(), "stop"), new AgentFreshDataPolicy());
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(3, 2, java.time.Duration.ofSeconds(1)));
    state.recordSuccessfulTool(AgentFreshnessPolicy.USER_MESSAGE_HISTORY);
    state.recordSuccessfulToolResult(AgentToolResult.success("other_tool", "{}"));
    state.markFreshSynthesisCorrectionUsed();

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () ->
                coordinator.process(
                    new LlmResponse("answer", List.of(), "stop"),
                    new java.util.ArrayList<>(),
                    List.<JsonObject>of(),
                    List.of(),
                    Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY),
                    Optional.empty(),
                    new AgentContext("room", "nick", null, null, false, List.of()),
                    null,
                    state,
                    "correlation",
                    (context, call, toolResult) -> toolResult.content(),
                    (definitions, toolName) -> definitions));

    assertEquals(
        "Agent did not produce a complete fresh history synthesis", exception.getMessage());
  }

  @Test
  void rejectsACorrectionResponseThatStillLacksFreshEvidence() {
    AgentFreshDataCoordinator coordinator =
        new AgentFreshDataCoordinator(
            request -> new LlmResponse("still incomplete", List.of(), "stop"),
            new AgentFreshDataPolicy());
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(3, 2, java.time.Duration.ofSeconds(1)));
    state.recordSuccessfulTool(AgentFreshnessPolicy.USER_MESSAGE_HISTORY);
    state.recordSuccessfulToolResult(AgentToolResult.success("other_tool", "{}"));

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () ->
                coordinator.process(
                    new LlmResponse("answer", List.of(), "stop"),
                    new java.util.ArrayList<>(),
                    List.<JsonObject>of(),
                    List.of(),
                    Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY),
                    Optional.empty(),
                    new AgentContext("room", "nick", null, null, false, List.of()),
                    null,
                    state,
                    "correlation",
                    (context, call, toolResult) -> toolResult.content(),
                    (definitions, toolName) -> definitions));

    assertEquals(
        "Agent did not produce a complete fresh history synthesis", exception.getMessage());
  }
}
