package org.saturn.app.agent;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Filters reflected Saturn command definitions unless the newest request names the command. */
final class AgentCommandIntentPolicy {
  private static final String COMMAND_TOOL_PREFIX = "saturn_";

  private AgentCommandIntentPolicy() {}

  static List<JsonObject> filter(
      List<JsonObject> definitions, AgentInvocationMode mode, String newestPrompt) {
    if (mode == AgentInvocationMode.MODERATION) {
      return List.copyOf(definitions);
    }
    List<JsonObject> filtered = new ArrayList<>();
    for (JsonObject definition : definitions) {
      String toolName = AgentToolDefinitionJson.functionName(definition).orElse("");
      if (!toolName.startsWith(COMMAND_TOOL_PREFIX) || explicitlyRequests(toolName, newestPrompt)) {
        filtered.add(definition);
      }
    }
    return List.copyOf(filtered);
  }

  private static boolean explicitlyRequests(String toolName, String newestPrompt) {
    String alias = toolName.substring(COMMAND_TOOL_PREFIX.length());
    String[] tokens = newestPrompt == null ? new String[0] : newestPrompt.strip().split("\\s+");
    return (tokens.length > 0 && alias.equals(tokens[0]))
        || (tokens.length > 1
            && ("run".equals(tokens[0]) || "execute".equals(tokens[0]))
            && alias.equals(tokens[1]));
  }
}
