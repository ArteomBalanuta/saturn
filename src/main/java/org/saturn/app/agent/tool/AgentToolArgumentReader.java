package org.saturn.app.agent.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Optional;

/** Reads optional, trimmed, non-blank string arguments from a tool request. */
final class AgentToolArgumentReader {
  private AgentToolArgumentReader() {}

  static Optional<String> nonBlankString(JsonObject arguments, String name) {
    JsonElement value = arguments.get(name);
    if (value == null
        || !value.isJsonPrimitive()
        || !value.getAsJsonPrimitive().isString()
        || value.getAsString().isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value.getAsString().trim());
  }
}
