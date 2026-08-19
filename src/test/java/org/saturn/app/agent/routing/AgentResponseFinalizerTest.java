package org.saturn.app.agent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentInvocation;
import org.saturn.app.agent.api.AgentInvocationMode;
import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.agent.api.AgentRoutingException;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.agent.config.AgentConfigLoader;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.turn.AgentFreshDataFinalValidator;
import org.saturn.app.agent.turn.AgentFreshDataPolicy;

class AgentResponseFinalizerTest {
  @Test
  void preparesVisibleResponseContent() throws Exception {
    AgentResponseFinalizer finalizer = finalizer();

    AgentResponseFinalizer.Result result =
        finalizer.prepare(
            invocation(AgentInvocationMode.DIRECT),
            new LlmResponse(
                " \"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\" — Pride and Prejudice, Jane Austen ",
                List.of(),
                "stop"),
            List.of(),
            Optional.empty(),
            List.of(),
            "correlation-1");

    assertTrue(result.shouldReply());
    assertEquals(
        "\"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\" — Pride and Prejudice, Jane Austen",
        result.content());
  }

  @Test
  void rejectsOrdinaryDirectProse() {
    AgentResponseFinalizer finalizer = finalizer();

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () ->
                finalizer.prepare(
                    invocation(AgentInvocationMode.DIRECT),
                    new LlmResponse("There are users in the room.", List.of(), "stop"),
                    List.of(),
                    Optional.empty(),
                    List.of(),
                    "correlation-prose"));

    assertEquals(
        "Agent returned a non-quote prose response after correction", exception.getMessage());
  }

  @Test
  void allowsOrdinaryProseWhenTheTurnHasSuccessfulToolEvidence() throws Exception {
    AgentResponseFinalizer finalizer = finalizer();

    AgentResponseFinalizer.Result result =
        finalizer.prepare(
            invocation(AgentInvocationMode.DIRECT),
            new LlmResponse("There are users in the room.", List.of(), "stop"),
            List.of(),
            Optional.empty(),
            List.of(AgentToolResult.success("room_users", "room")),
            "correlation-tool",
            false);

    assertTrue(result.shouldReply());
    assertEquals("There are users in the room.", result.content());
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
            List.of(AgentToolResult.success("run_command", "help output")),
            "correlation-2",
            false);

    assertFalse(result.shouldReply());
    assertEquals("", result.content());
  }

  @Test
  void rejectsNoReplyMarkerForARequiredDirectResponse() {
    AgentResponseFinalizer finalizer = finalizer();

    assertThrows(
        AgentRoutingException.class,
        () ->
            finalizer.prepare(
                invocation(AgentInvocationMode.DIRECT),
                new LlmResponse("[[SATURN_NO_REPLY]]", List.of(), "stop"),
                List.of(),
                Optional.empty(),
                List.of(),
                "correlation-3"));
  }

  @Test
  void rejectsAnActuallyEmptyResponse() {
    AgentResponseFinalizer finalizer = finalizer();

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () ->
                finalizer.prepare(
                    invocation(AgentInvocationMode.DIRECT),
                    new LlmResponse("", List.of(), "stop"),
                    List.of(),
                    Optional.empty(),
                    List.of(),
                    "correlation-empty"));

    assertEquals("Agent returned an empty response", exception.getMessage());
  }

  @Test
  void rejectsNullProviderResponseWithStableRoutingError() {
    AgentResponseFinalizer finalizer = finalizer();

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () ->
                finalizer.prepare(
                    invocation(AgentInvocationMode.DIRECT),
                    null,
                    List.of(),
                    Optional.empty(),
                    List.of(),
                    "correlation-null"));

    assertEquals("Agent returned no response", exception.getMessage());
  }

  private static AgentResponseFinalizer finalizer() {
    LlmClient client = request -> new LlmResponse("{\"line\":\"unused\"}", List.of(), "stop");
    AgentConfig config = AgentConfigLoader.load(null, Map.of());
    AgentParticipationConfig participation = AgentParticipationConfig.from(null);
    return new AgentResponseFinalizer(
        new AgentResponseCorrector(client),
        new AgentFreshDataFinalValidator(new AgentFreshDataPolicy()),
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
