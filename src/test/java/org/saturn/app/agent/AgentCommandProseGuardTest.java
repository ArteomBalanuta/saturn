package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmToolCall;
import org.saturn.app.agent.tool.RunCommandTool;

class AgentCommandProseGuardTest {
  @Test
  void detectsOnlyMarkdownWrappedCommandsExposedToTheCaller() {
    AgentCommandProseGuard guard = guardFor(regularContext());

    assertEquals(Optional.of("weather"), guard.findCommand("As requested: `weather charlotte`"));
    assertEquals(Optional.of("weather"), guard.findCommand("As requested: ``weather charlotte``"));
    assertEquals(Optional.of("time"), guard.findCommand("```text\n*time Tokyo\n```"));
    assertEquals(Optional.of("time"), guard.findCommand("~~~text\n*time Tokyo\n~~~"));
    assertTrue(guard.findCommand("Use `List.of()` here").isEmpty());
    assertTrue(guard.findCommand("`kick bob`").isEmpty());
    assertTrue(
        guard
            .findCommand(
                "Weather activity appears in the user's message history, but no live lookup was performed.")
            .isEmpty());
  }

  @Test
  void acceptsOnlyMatchingStructuredRunCommandCalls() {
    AgentCommandProseGuard guard = guardFor(regularContext());

    assertTrue(
        guard.matches(
            new LlmToolCall(
                "call-1", "run_command", "{\"command\":\"weather\",\"arguments\":\"charlotte\"}"),
            "weather"));
    assertFalse(
        guard.matches(
            new LlmToolCall("call-2", "run_command", "{\"command\":\"help\"}"), "weather"));
    assertFalse(
        guard.matches(
            new LlmToolCall("call-3", "run_command", "{\"command\":\"WEATHER\"}"), "weather"));
    assertFalse(guard.matches(new LlmToolCall("call-4", "run_command", "not-json"), "weather"));
  }

  @Test
  void rejectsRunCommandArgumentsThatViolateThePublishedSchema() {
    AgentCommandProseGuard guard = guardFor(regularContext());

    assertFalse(
        guard.matches(
            new LlmToolCall("call-1", "run_command", "{\"command\":\"weather\",\"arguments\":42}"),
            "weather"));
    assertFalse(
        guard.matches(
            new LlmToolCall(
                "call-2",
                "run_command",
                "{\"command\":\"weather\",\"arguments\":\"Tokyo\",\"extra\":true}"),
            "weather"));
  }

  @Test
  void rejectsMalformedExecutedCommandsAndEmptyProse() {
    AgentCommandProseGuard guard = guardFor(regularContext());

    assertTrue(guard.findCommand(null).isEmpty());
    assertTrue(guard.findCommand("   ").isEmpty());
    assertTrue(
        guard
            .executedCommand(new LlmToolCall("call-1", "say", "{\"command\":\"weather\"}"))
            .isEmpty());
    assertTrue(
        guard.executedCommand(new LlmToolCall("call-2", "run_command", "not-json")).isEmpty());
    assertTrue(
        guard
            .executedCommand(new LlmToolCall("call-3", "run_command", "{\"command\":42}"))
            .isEmpty());
    assertEquals(
        Optional.of("weather"),
        guard.executedCommand(
            new LlmToolCall("call-4", "run_command", "{\"command\":\"WEATHER\"}")));
  }

  @Test
  void rejectsEmptyAndUnauthorizedCommandShapes() {
    AgentCommandProseGuard guard = guardFor(regularContext());

    assertTrue(guard.findCommand("```text\n\n```").isEmpty());
    assertFalse(
        guard.matches(
            new LlmToolCall("call-1", "other_tool", "{\"command\":\"weather\"}"), "weather"));
    assertFalse(
        guard.matches(
            new LlmToolCall("call-2", "run_command", "{\"command\":\"weather\"}"), "unknown"));
    assertFalse(guard.matches(new LlmToolCall("call-3", "run_command", "null"), "weather"));
    assertFalse(
        guard.matches(new LlmToolCall("call-4", "run_command", "{\"command\":{}}"), "weather"));
    assertFalse(
        guard.matches(
            new LlmToolCall("call-5", "run_command", "{\"command\":\"weather\",\"arguments\":[]}"),
            "weather"));
    assertTrue(guard.executedCommand(new LlmToolCall("call-6", "run_command", "null")).isEmpty());
  }

  private AgentCommandProseGuard guardFor(AgentContext context) {
    var definitions =
        new AgentToolRegistry()
            .register(new RunCommandTool((ignored, command, arguments) -> true))
            .freeze()
            .definitions(context);
    List<JsonObject> values = new ArrayList<>();
    definitions.forEach(element -> values.add(element.getAsJsonObject()));
    return AgentCommandProseGuard.from(values);
  }

  private AgentContext regularContext() {
    return new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of("alice"));
  }
}
