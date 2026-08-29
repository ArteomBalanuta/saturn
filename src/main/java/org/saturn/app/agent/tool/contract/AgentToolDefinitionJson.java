package org.saturn.app.agent.tool.contract;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Optional;

/** Reads the function name from an OpenAI-compatible tool definition. */
public final class AgentToolDefinitionJson {
  /** Implements the {@code AgentToolDefinitionJson} operation for this agent component. */
  private AgentToolDefinitionJson() {}

  /**
   * Implements the {@code functionName} operation for this agent component.
   *
   * @param definition input argument used by this operation
   * @return the operation result
   */
  public static Optional<String> functionName(JsonObject definition) {
    if (definition == null) {
      return Optional.empty();
    }
    JsonElement function = definition.get("function");
    if (function == null || !function.isJsonObject()) {
      return Optional.empty();
    }
    JsonElement name = function.getAsJsonObject().get("name");
    return name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()
        ? Optional.of(name.getAsString())
        : Optional.empty();
  }
}
