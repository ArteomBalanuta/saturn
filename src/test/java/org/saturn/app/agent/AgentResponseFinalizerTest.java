package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmResponse;

class AgentResponseFinalizerTest {
  @Test
  void preparesVisibleResponseContent() throws Exception {
    AgentResponseFinalizer finalizer = finalizer();

    AgentResponseFinalizer.Result result =
        finalizer.prepare(
            invocation(AgentInvocationMode.DIRECT),
            new LlmResponse(" answer [[SATURN_NO_REPLY]] ", List.of(), "stop"),
            List.of(),
            Optional.empty(),
            List.of(),
            "correlation-1");

    assertTrue(result.shouldReply());
    assertEquals("answer", result.content());
  }

  @Test
  void suppressesModerationResponses() throws Exception {
    AgentResponseFinalizer finalizer = finalizer();

    AgentResponseFinalizer.Result result =
        finalizer.prepare(
            invocation(AgentInvocationMode.MODERATION),
            new LlmResponse("internal", List.of(), "stop"),
            List.of(),
            Optional.empty(),
            List.of(),
            "correlation-2");

    assertFalse(result.shouldReply());
    assertEquals("", result.content());
  }

  private static AgentResponseFinalizer finalizer() {
    LlmClient client = request -> new LlmResponse("unused", List.of(), "stop");
    AgentConfig config = AgentConfig.from(null, Map.of());
    AgentParticipationConfig participation = AgentParticipationConfig.from(null);
    return new AgentResponseFinalizer(
        new AgentResponseCorrector(client),
        new AgentFreshDataCoordinator(client, new AgentFreshDataPolicy()),
        participation,
        config.maxOutputChars());
  }

  private static AgentInvocation invocation(AgentInvocationMode mode) {
    return new AgentInvocation(
        "request-1",
        new AgentContext("programming", "alice", "trip", "hash", false, List.of("alice")),
        "hello",
        mode,
        "hello");
  }
}
