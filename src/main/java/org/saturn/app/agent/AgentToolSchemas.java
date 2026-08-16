package org.saturn.app.agent;

import com.google.gson.JsonObject;

/** Factory for the common JSON-object schema shape used by agent tool contracts. */
public final class AgentToolSchemas {
  private AgentToolSchemas() {}

  public static JsonObject object() {
    return object(true);
  }

  public static JsonObject closedObject() {
    return object(false);
  }

  private static JsonObject object(boolean additionalProperties) {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", new JsonObject());
    schema.addProperty("additionalProperties", additionalProperties);
    return schema;
  }
}
