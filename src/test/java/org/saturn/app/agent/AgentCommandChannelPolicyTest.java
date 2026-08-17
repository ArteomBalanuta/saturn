package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.llm.LlmToolCall;
import org.saturn.app.agent.tool.RunCommandTool;

class AgentCommandChannelPolicyTest {
  @Test
  void requiresStructuredToolCallForRenderedCommand() throws Exception {
    List<JsonObject> definitions = definitions();
    LlmToolCall call =
        new LlmToolCall(
            "call-1", "run_command", "{\"command\":\"weather\",\"arguments\":\"Tokyo\"}");
    AgentCommandChannelPolicy policy =
        new AgentCommandChannelPolicy(request -> new LlmResponse("", List.of(call), "tool_calls"));
    List<LlmMessage> messages = new ArrayList<>();

    AgentTurnPolicyResult result =
        ((AgentTurnPolicy) policy)
            .apply(
                new AgentTurnPolicyInput(
                    new LlmResponse("`weather Tokyo`", List.of(), "stop"),
                    messages,
                    definitions,
                    AgentCommandProseGuard.from(definitions),
                    new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1))),
                    "show Tokyo weather",
                    "request-1"));

    assertEquals(List.of(call), result.response().toolCalls());
    assertFalse(messages.isEmpty());
  }

  @Test
  void acceptsNonCommandFallbackWithExactlyTheDeclaredResponseShape() throws Exception {
    LlmToolCall call =
        new LlmToolCall(
            "call-1",
            "respond_without_command",
            "{\"response\":\"The weather tool is unavailable.\"}");
    AgentCommandChannelPolicy policy =
        new AgentCommandChannelPolicy(request -> new LlmResponse("", List.of(call), "tool_calls"));
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1)));

    AgentCommandChannelPolicy.Result result =
        policy.enforce(
            new LlmResponse("`weather Tokyo`", List.of(), "stop"),
            new ArrayList<>(),
            definitions(),
            AgentCommandProseGuard.from(definitions()),
            state,
            "show Tokyo weather",
            "request-1");

    assertEquals("The weather tool is unavailable.", result.response().content());
    assertTrue(result.response().toolCalls().isEmpty());
  }

  @Test
  void rejectsMultipleOrMismatchedCorrectionCalls() {
    List<LlmToolCall> calls =
        List.of(
            new LlmToolCall("call-1", "run_command", "{}"),
            new LlmToolCall("call-2", "run_command", "{}"));
    AgentCommandChannelPolicy policy =
        new AgentCommandChannelPolicy(request -> new LlmResponse("", calls, "tool_calls"));
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1)));

    assertThrows(
        AgentRoutingException.class,
        () ->
            policy.enforce(
                new LlmResponse("`weather Tokyo`", List.of(), "stop"),
                new ArrayList<>(),
                definitions(),
                AgentCommandProseGuard.from(definitions()),
                state,
                "show Tokyo weather",
                "request-1"));
  }

  @Test
  void rejectsMalformedNonCommandFallbackArguments() {
    LlmToolCall call = new LlmToolCall("call-1", "respond_without_command", "{\"response\":4}");
    AgentCommandChannelPolicy policy =
        new AgentCommandChannelPolicy(request -> new LlmResponse("", List.of(call), "tool_calls"));
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1)));

    assertThrows(
        AgentRoutingException.class,
        () ->
            policy.enforce(
                new LlmResponse("`weather Tokyo`", List.of(), "stop"),
                new ArrayList<>(),
                definitions(),
                AgentCommandProseGuard.from(definitions()),
                state,
                "show Tokyo weather",
                "request-1"));
  }

  @Test
  void rejectsMissingOrObjectNonCommandFallbackResponse() {
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1)));

    for (String arguments : List.of("{}", "{\"response\":{}}", "{\"response\":null}")) {
      LlmToolCall call = new LlmToolCall("call-1", "respond_without_command", arguments);
      AgentCommandChannelPolicy policy =
          new AgentCommandChannelPolicy(
              request -> new LlmResponse("", List.of(call), "tool_calls"));

      assertThrows(
          AgentRoutingException.class,
          () ->
              policy.enforce(
                  new LlmResponse("`weather Tokyo`", List.of(), "stop"),
                  new ArrayList<>(),
                  definitions(),
                  AgentCommandProseGuard.from(definitions()),
                  state,
                  "show Tokyo weather",
                  "request-invalid-shape"));
    }
  }

  @Test
  void rejectsCommandProseRepeatedAfterAnUnsuccessfulCorrection() {
    AgentCommandChannelPolicy policy =
        new AgentCommandChannelPolicy(
            request -> new LlmResponse("`weather Tokyo`", List.of(), "stop"));
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1)));
    state.markCommandCorrectionUsed();

    assertThrows(
        AgentRoutingException.class,
        () ->
            policy.enforce(
                new LlmResponse("`weather Tokyo`", List.of(), "stop"),
                new ArrayList<>(),
                definitions(),
                AgentCommandProseGuard.from(definitions()),
                state,
                "show Tokyo weather",
                "request-repeated-command"));
  }

  @Test
  void doesNotOfferToolsAgainAfterCommandFailure() throws Exception {
    List<LlmRequest> requests = new ArrayList<>();
    AgentCommandChannelPolicy policy =
        new AgentCommandChannelPolicy(
            request -> {
              requests.add(request);
              return new LlmResponse("The command did not complete.", List.of(), "stop");
            });
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1)));
    state.recordFailedCommand("weather");

    AgentCommandChannelPolicy.Result result =
        policy.enforce(
            new LlmResponse("`weather Tokyo`", List.of(), "stop"),
            new ArrayList<>(),
            definitions(),
            AgentCommandProseGuard.from(definitions()),
            state,
            "show Tokyo weather",
            "request-failed");

    assertEquals("The command did not complete.", result.response().content());
    assertEquals(1, requests.size());
    assertTrue(requests.getFirst().tools().isEmpty());
  }

  @Test
  void doesNotOfferToolsAgainAfterSuccessfulCommand() throws Exception {
    List<LlmRequest> requests = new ArrayList<>();
    AgentCommandChannelPolicy policy =
        new AgentCommandChannelPolicy(
            request -> {
              requests.add(request);
              return new LlmResponse("The command already completed.", List.of(), "stop");
            });
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1)));
    state.recordSuccessfulCommand("weather");

    AgentCommandChannelPolicy.Result result =
        policy.enforce(
            new LlmResponse("`weather Tokyo`", List.of(), "stop"),
            new ArrayList<>(),
            definitions(),
            AgentCommandProseGuard.from(definitions()),
            state,
            "show Tokyo weather",
            "request-succeeded");

    assertEquals("The command already completed.", result.response().content());
    assertEquals(1, requests.size());
    assertTrue(requests.getFirst().tools().isEmpty());
  }

  @Test
  void rejectsBlankOrCommandContainingNonCommandFallbackContent() {
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1)));

    for (String response : List.of("   ", "`weather Tokyo`")) {
      LlmToolCall call =
          new LlmToolCall(
              "call-1", "respond_without_command", "{\"response\":" + quote(response) + "}");
      AgentCommandChannelPolicy policy =
          new AgentCommandChannelPolicy(
              request -> new LlmResponse("", List.of(call), "tool_calls"));

      assertThrows(
          AgentRoutingException.class,
          () ->
              policy.enforce(
                  new LlmResponse("`weather Tokyo`", List.of(), "stop"),
                  new ArrayList<>(),
                  definitions(),
                  AgentCommandProseGuard.from(definitions()),
                  state,
                  "show Tokyo weather",
                  "request-invalid-content"));
    }
  }

  @Test
  void rejectsNullCommandCorrectionWithStableRoutingError() {
    AgentCommandChannelPolicy policy = new AgentCommandChannelPolicy(request -> null);
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1)));

    assertThrows(
        AgentRoutingException.class,
        () ->
            policy.enforce(
                new LlmResponse("`weather Tokyo`", List.of(), "stop"),
                new ArrayList<>(),
                definitions(),
                AgentCommandProseGuard.from(definitions()),
                state,
                "show Tokyo weather",
                "request-null"));
  }

  @Test
  void rejectsNullInitialResponseWithStableRoutingError() {
    AgentCommandChannelPolicy policy =
        new AgentCommandChannelPolicy(request -> new LlmResponse("", List.of(), "stop"));
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1)));

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () ->
                policy.enforce(
                    null,
                    new ArrayList<>(),
                    definitions(),
                    AgentCommandProseGuard.from(definitions()),
                    state,
                    "show Tokyo weather",
                    "request-null-initial"));

    assertEquals("Agent returned no response", exception.getMessage());
  }

  private static List<JsonObject> definitions() {
    AgentContext context =
        new AgentContext("programming", "alice", "trip", "hash", false, List.of("alice"));
    var values =
        new AgentToolRegistry()
            .register(new RunCommandTool((ignored, command, arguments) -> true))
            .freeze()
            .definitions(context);
    List<JsonObject> definitions = new ArrayList<>();
    values.forEach(value -> definitions.add(value.getAsJsonObject()));
    return List.copyOf(definitions);
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
