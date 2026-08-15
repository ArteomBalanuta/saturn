package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class AgentToolTest {
  @Test
  void legacyToolReceivesSafeDescriptorDefaults() {
    AgentTool tool =
        new AgentTool() {
          @Override
          public String name() {
            return "legacy_tool";
          }

          @Override
          public String description() {
            return "Legacy tool description.";
          }

          @Override
          public JsonObject parameters() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", new JsonObject());
            return schema;
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), "ok");
          }
        };

    AgentToolDescriptor descriptor = tool.descriptor(null);

    assertEquals("legacy_tool", descriptor.name());
    assertEquals("legacy_tool", descriptor.label());
    assertEquals("Legacy tool description.", descriptor.description());
    assertEquals("general", descriptor.category());
    assertEquals(ToolAccess.PUBLIC, descriptor.access());
    assertEquals(ToolEffect.READ_ONLY, descriptor.effect());
    assertEquals(ToolResultMode.MODEL_DATA, descriptor.resultMode());
    assertEquals("object", descriptor.parameters().get("type").getAsString());
  }
}
