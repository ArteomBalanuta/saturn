package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmToolCall;

class AgentToolResultCoordinatorTest {
  @Test
  void recordsSuccessfulCommandsAndAppendsRenderedToolMessages() throws Exception {
    AgentContext context =
        new AgentContext("programming", "alice", "trip", "hash", false, List.of("alice"));
    AgentToolResultCoordinator coordinator =
        new AgentToolResultCoordinator(
            new AgentFreshDataPolicy(), AgentCommandProseGuard.from(List.of()));
    AgentTurnState state =
        new AgentTurnState(AgentExecutionLimits.from(AgentConfig.from(null, Map.of())));
    LlmToolCall call =
        new LlmToolCall("call-1", "run_command", "{\"command\":\"help\",\"arguments\":\"\"}");
    List<LlmMessage> messages = new java.util.ArrayList<>();

    coordinator.record(
        context,
        List.of(call),
        List.of(new AgentToolResult("call-1", "run_command", "help output", false)),
        Optional.empty(),
        Optional.empty(),
        state,
        messages,
        (ignoredContext, ignoredCall, result) -> result.envelopeJson(),
        "correlation-1");

    assertTrue(state.hasSuccessfulTool("run_command"));
    assertEquals(1, state.successfulToolResults().size());
    assertEquals(1, messages.size());
    assertEquals("call-1", messages.getFirst().toolCallId());
  }

  @Test
  void rejectsFailedRequiredFreshTool() {
    AgentToolResultCoordinator coordinator =
        new AgentToolResultCoordinator(
            new AgentFreshDataPolicy(), AgentCommandProseGuard.from(List.of()));
    AgentTurnState state =
        new AgentTurnState(AgentExecutionLimits.from(AgentConfig.from(null, Map.of())));
    LlmToolCall call = new LlmToolCall("call-2", "user_message_history", "{}");

    org.junit.jupiter.api.Assertions.assertThrows(
        AgentRoutingException.class,
        () ->
            coordinator.record(
                new AgentContext("programming", "alice", "trip", "hash", false, List.of("alice")),
                List.of(call),
                List.of(AgentToolResult.error("call-2", "user_message_history", "failed")),
                Optional.of("user_message_history"),
                Optional.of("alice"),
                state,
                new java.util.ArrayList<>(),
                (ignoredContext, ignoredCall, result) -> result.envelopeJson(),
                "correlation-2"));
  }
}
