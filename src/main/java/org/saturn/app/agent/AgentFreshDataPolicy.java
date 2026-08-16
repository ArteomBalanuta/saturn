package org.saturn.app.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.llm.LlmToolCall;

/** Validates the evidence required before the router may return a fresh-data synthesis. */
final class AgentFreshDataPolicy {
  boolean requiresHistorySynthesis(Optional<String> requiredTool) {
    return requiredTool.filter("user_message_history"::equals).isPresent();
  }

  boolean satisfiesProfileContract(LlmResponse response, List<AgentToolResult> results) {
    boolean hasHistory =
        results.stream().anyMatch(result -> "user_message_history".equals(result.toolName()));
    return hasHistory && response.content() != null && !response.content().isBlank();
  }

  LlmResponse requireExactToolCall(
      LlmResponse response, String toolName, Optional<String> expectedNick)
      throws AgentRoutingException {
    if (response.toolCalls().size() != 1
        || !toolName.equals(response.toolCalls().getFirst().name())
        || !matchesTarget(response.toolCalls().getFirst(), expectedNick)) {
      throw new AgentRoutingException(
          "Agent did not call exactly the required fresh-data tool: " + toolName);
    }
    return response;
  }

  boolean matchesTarget(LlmToolCall call, Optional<String> expectedNick) {
    if (expectedNick.isEmpty() || !"user_message_history".equals(call.name())) return true;
    try {
      JsonElement nick = JsonParser.parseString(call.arguments()).getAsJsonObject().get("nick");
      return nick != null
          && nick.isJsonPrimitive()
          && expectedNick.orElseThrow().equalsIgnoreCase(nick.getAsString().trim());
    } catch (JsonParseException | IllegalStateException exception) {
      return false;
    }
  }
}
