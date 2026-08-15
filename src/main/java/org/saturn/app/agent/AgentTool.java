package org.saturn.app.agent;

import com.google.gson.JsonObject;
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

  default boolean isAvailableTo(AgentContext context) {
    return true;
  }

  default Set<String> requiredSuccessfulTools() {
    return Set.of();
  }

  AgentToolResult execute(AgentContext context, JsonObject arguments);
}
