package org.saturn.app.agent.tool.contract;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Optional;

/** Reads the function name from an OpenAI-compatible tool definition. */
public final class AgentToolDefinitionJson {
  private AgentToolDefinitionJson() {}

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
