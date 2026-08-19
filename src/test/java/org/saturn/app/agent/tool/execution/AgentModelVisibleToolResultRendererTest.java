package org.saturn.app.agent.tool.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.api.ToolAccess;
import org.saturn.app.agent.api.ToolEffect;
import org.saturn.app.agent.api.ToolResponseEnvelope;
import org.saturn.app.agent.api.ToolResultMode;
import org.saturn.app.agent.llm.LlmToolCall;
import org.saturn.app.agent.tool.contract.AgentToolSchemas;

class AgentModelVisibleToolResultRendererTest {
  private static final AgentContext CONTEXT =
      new AgentContext("programming", "alice", "trip", "hash", false, List.of("alice"));

  @Test
  void preservesErrorEnvelopeAndStandardModelData() {
    AgentModelVisibleToolResultRenderer renderer =
        new AgentModelVisibleToolResultRenderer(new AgentToolRegistry().freeze());

    assertEquals(
        AgentToolResult.error("call", "weather", "failed").envelopeJson(),
        renderer.render(
            CONTEXT,
            new LlmToolCall("call", "weather", "{}"),
            AgentToolResult.error("call", "weather", "failed")));
    assertEquals(
        AgentToolResult.success("weather", "{\"temperature\": 20}").envelopeJson(),
        renderer.render(
            CONTEXT,
            new LlmToolCall("call", "weather", "{}"),
            AgentToolResult.success("weather", "{\"temperature\": 20}")));
  }

  @Test
  void rendersRoomDeliveryAsModelVisibleSuccessEnvelope() {
    AgentTool roomTool =
        new AgentTool() {
          @Override
          public String name() {
            return "announce";
          }

          @Override
          public AgentToolDescriptor descriptor(AgentContext context) {
            return new AgentToolDescriptor(
                name(),
                name(),
                name(),
                "general",
                ToolAccess.PUBLIC,
                ToolEffect.READ_ONLY,
                ToolResultMode.ROOM_DELIVERY,
                AgentToolSchemas.object(),
                List.of(),
                List.of("Do not use outside room delivery."),
                List.of(),
                Set.of(),
                Set.of());
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), "ignored");
          }
        };
    AgentModelVisibleToolResultRenderer renderer =
        new AgentModelVisibleToolResultRenderer(
            new AgentToolRegistry().register(roomTool).freeze());

    assertEquals(
        ToolResponseEnvelope.success(
                "Tool output was delivered to the room. Do not repeat or paraphrase it.")
            .toJson(),
        renderer.render(
            CONTEXT,
            new LlmToolCall("call", "announce", "{}"),
            AgentToolResult.success("announce", "ignored")));
  }
}
