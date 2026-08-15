package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentToolDescriptorTest {
  @Test
  void rejectsInvalidDescriptorMetadata() {
    JsonObject parameters = parameters();

    assertThrows(
        IllegalArgumentException.class,
        () -> descriptor("", "label", "description", "category", parameters));
    assertThrows(
        IllegalArgumentException.class,
        () -> descriptor("invalid-name", "label", "description", "category", parameters));
    assertThrows(
        IllegalArgumentException.class,
        () -> descriptor("name", "", "description", "category", parameters));
    assertThrows(
        IllegalArgumentException.class,
        () -> descriptor("name", "label", "", "category", parameters));
    assertThrows(
        IllegalArgumentException.class,
        () -> descriptor("name", "label", "description", "", parameters));
    assertThrows(
        NullPointerException.class,
        () ->
            new AgentToolDescriptor(
                "name",
                "label",
                "description",
                "category",
                null,
                ToolEffect.READ_ONLY,
                ToolResultMode.MODEL_DATA,
                parameters,
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Set.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AgentToolDescriptor(
                "name",
                "label",
                "description",
                "category",
                ToolAccess.PUBLIC,
                ToolEffect.READ_ONLY,
                ToolResultMode.MODEL_DATA,
                new JsonObject(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Set.of()));
  }

  @Test
  void makesCollectionsAndExamplesImmutable() {
    List<String> whenToUse = new ArrayList<>(List.of("answer current room questions"));
    Set<String> capabilities = Set.of("ROOM_READ");
    AgentToolDescriptor descriptor =
        new AgentToolDescriptor(
            "room_users",
            "Room users",
            "Read current users in a room.",
            "room_context",
            ToolAccess.PUBLIC,
            ToolEffect.READ_ONLY,
            ToolResultMode.MODEL_DATA,
            parameters(),
            whenToUse,
            List.of("Do not use for historical presence."),
            List.of(new ToolExample("room_users", "{}", "List current room users.")),
            capabilities,
            Set.of());

    whenToUse.add("mutate descriptor");

    assertEquals(List.of("answer current room questions"), descriptor.whenToUse());
    assertEquals("room_users", descriptor.examples().getFirst().toolName());
    assertThrows(
        UnsupportedOperationException.class,
        () -> descriptor.whenToUse().add("mutate descriptor"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> descriptor.requiredCapabilities().add("ADMIN"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> descriptor.examples().add(new ToolExample("other", "{}", "other")));
  }

  @Test
  void rejectsARequiredParameterThatIsNotDeclared() {
    JsonObject schema = parameters();
    com.google.gson.JsonArray required = new com.google.gson.JsonArray();
    required.add("missing");
    schema.add("required", required);

    assertThrows(
        IllegalArgumentException.class,
        () -> descriptor("tool", "Tool", "Reads data.", "test", schema));
  }

  @Test
  void requiresADeclaredNegativeConstraint() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AgentToolDescriptor(
                "tool",
                "Tool",
                "Reads data.",
                "test",
                ToolAccess.PUBLIC,
                ToolEffect.READ_ONLY,
                ToolResultMode.MODEL_DATA,
                parameters(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Set.of()));
  }

  @Test
  void rejectsUnsupportedSchemaTypesBeforeToolRegistration() {
    JsonObject parameterSchema = parameters();
    JsonObject property = new JsonObject();
    property.addProperty("type", "unknown");
    parameterSchema.getAsJsonObject("properties").add("value", property);

    assertThrows(IllegalArgumentException.class, () -> AgentToolSchemaValidator.validateSchema(parameterSchema));

    JsonObject resultSchema = new JsonObject();
    resultSchema.addProperty("type", "unknown");
    assertThrows(
        IllegalArgumentException.class, () -> AgentToolSchemaValidator.validateResultSchema(resultSchema));
  }

  private AgentToolDescriptor descriptor(
      String name, String label, String description, String category, JsonObject parameters) {
    return new AgentToolDescriptor(
        name,
        label,
        description,
        category,
        ToolAccess.PUBLIC,
        ToolEffect.READ_ONLY,
        ToolResultMode.MODEL_DATA,
        parameters,
        List.of(),
        List.of("Do not use for unrelated work."),
        List.of(),
        Set.of(),
        Set.of());
  }

  private JsonObject parameters() {
    JsonObject parameters = new JsonObject();
    parameters.addProperty("type", "object");
    parameters.add("properties", new JsonObject());
    return parameters;
  }
}
