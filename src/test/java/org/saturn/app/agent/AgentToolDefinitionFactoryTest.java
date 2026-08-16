package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentToolDefinitionFactoryTest {
  @Test
  void serializesTheValidatedSdkDescriptorAsAnOpenAiFunction() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", new JsonObject());
    schema.addProperty("additionalProperties", false);
    AgentToolDescriptor descriptor =
        new AgentToolDescriptor(
            "room_users",
            "Room users",
            "List users currently in a room.",
            "room_context",
            ToolAccess.PUBLIC,
            ToolEffect.READ_ONLY,
            ToolResultMode.MODEL_DATA,
            schema,
            List.of("Use for current room presence."),
            List.of("Do not use for historical activity."),
            List.of(new ToolExample("room_users", "{}", "List users.")),
            Set.of(),
            Set.of());

    JsonObject function = new AgentToolDefinitionFactory().create(descriptor);

    assertEquals("function", function.get("type").getAsString());
    JsonObject definition = function.getAsJsonObject("function");
    assertEquals("room_users", definition.get("name").getAsString());
    assertEquals("object", definition.getAsJsonObject("parameters").get("type").getAsString());
    assertTrue(definition.get("description").getAsString().contains("SATURN SDK CONTRACT"));
    assertTrue(definition.get("description").getAsString().contains("label: Room users"));
    assertTrue(definition.get("description").getAsString().contains("read_only: true"));
    assertTrue(definition.get("description").getAsString().contains("idempotent: true"));
    assertTrue(
        definition.get("description").getAsString().contains("result_schema: {\"type\":\"any\"}"));
  }

  @Test
  void rendersSetBasedContractMetadataInStableOrder() {
    AgentToolDescriptor descriptor =
        new AgentToolDescriptor(
            "ordered",
            "Ordered",
            "Test ordering.",
            "test",
            ToolAccess.PUBLIC,
            ToolEffect.READ_ONLY,
            ToolResultMode.MODEL_DATA,
            AgentToolSchemas.object(),
            List.of("Use it."),
            List.of("Do not misuse it."),
            List.of(),
            Set.of("zeta", "alpha"),
            Set.of("later", "first"));

    String description =
        new AgentToolDefinitionFactory()
            .create(descriptor)
            .getAsJsonObject("function")
            .get("description")
            .getAsString();

    assertTrue(description.indexOf("required_capabilities: alpha, zeta") >= 0);
    assertTrue(description.indexOf("required_successful_tools: first, later") >= 0);
  }
}
