package org.saturn.app.agent.tool.contract;

import com.google.gson.JsonObject;

/** Factory for the common JSON-object schema shape used by agent tool contracts. */
public final class AgentToolSchemas {
  /** Implements the {@code AgentToolSchemas} operation for this agent component. */
  private AgentToolSchemas() {}

  /**
   * Implements the {@code object} operation for this agent component.
   *
   * @return the operation result
   */
  public static JsonObject object() {
    return object(true);
  }

  /**
   * Implements the {@code closedObject} operation for this agent component.
   *
   * @return the operation result
   */
  public static JsonObject closedObject() {
    return object(false);
  }

  /**
   * Implements the {@code validateSchema} operation for this agent component.
   *
   * @param schema input argument used by this operation
   */
  public static void validateSchema(JsonObject schema) {
    AgentToolSchemaValidator.validateSchema(schema);
  }

  /**
   * Implements the {@code validateResultSchema} operation for this agent component.
   *
   * @param schema input argument used by this operation
   */
  public static void validateResultSchema(JsonObject schema) {
    AgentToolSchemaValidator.validateResultSchema(schema);
  }

  /**
   * Implements the {@code object} operation for this agent component.
   *
   * @param additionalProperties input argument used by this operation
   * @return the operation result
   */
  private static JsonObject object(boolean additionalProperties) {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", new JsonObject());
    schema.addProperty("additionalProperties", additionalProperties);
    return schema;
  }
}
