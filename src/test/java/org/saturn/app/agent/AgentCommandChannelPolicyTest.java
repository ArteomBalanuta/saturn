package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;
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

    AgentCommandChannelPolicy.Result result =
        policy.enforce(
            new LlmResponse("`weather Tokyo`", List.of(), "stop"),
            messages,
            definitions,
            AgentCommandProseGuard.from(definitions),
            new AgentTurnState(new AgentExecutionLimits(5, 10, Duration.ofSeconds(1))),
            "show Tokyo weather",
            "request-1");

    assertEquals(List.of(call), result.response().toolCalls());
    assertFalse(messages.isEmpty());
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
}
