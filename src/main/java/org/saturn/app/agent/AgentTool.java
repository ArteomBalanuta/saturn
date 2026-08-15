package org.saturn.app.agent;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;

public interface AgentTool {
  String name();

  default String description() {
    return name();
  }

  default JsonObject parameters() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", new JsonObject());
    schema.addProperty("additionalProperties", false);
    return schema;
  }

  default JsonObject parameters(AgentContext context) {
    return parameters();
  }

  default boolean isAvailableTo(AgentContext context) {
    return true;
  }

  default Set<String> requiredSuccessfulTools() {
    return Set.of();
  }

  default AgentToolDescriptor descriptor(AgentContext context) {
    return new AgentToolDescriptor(
        name(),
        name(),
        description(),
        "general",
        ToolAccess.PUBLIC,
        ToolEffect.READ_ONLY,
        ToolResultMode.MODEL_DATA,
        parameters(context),
        List.of(),
        List.of(),
        List.of(),
        Set.of(),
        requiredSuccessfulTools());
  }

  AgentToolResult execute(AgentContext context, JsonObject arguments);
}
