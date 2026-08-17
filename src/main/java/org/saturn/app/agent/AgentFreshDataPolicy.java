package org.saturn.app.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.llm.LlmToolCall;

/** Validates the evidence required before the router may return a fresh-data synthesis. */
final class AgentFreshDataPolicy {
  boolean requiresHistorySynthesis(Optional<String> requiredTool) {
    return requiredTool.filter(AgentFreshnessPolicy.USER_MESSAGE_HISTORY::equals).isPresent();
  }

  boolean satisfiesProfileContract(LlmResponse response, List<AgentToolResult> results) {
    if (response == null || results == null) return false;
    boolean hasHistory =
        results.stream()
            .anyMatch(
                result ->
                    result != null
                        && AgentFreshnessPolicy.USER_MESSAGE_HISTORY.equals(result.toolName()));
    return hasHistory && response.content() != null && !response.content().isBlank();
  }

  boolean requiresSynthesisCorrection(
      Optional<String> requiredTool, LlmResponse response, List<AgentToolResult> results) {
    return requiresHistorySynthesis(requiredTool)
        && response != null
        && !AgentResponseCorrector.isFailurePlaceholder(response)
        && !satisfiesProfileContract(response, results);
  }

  boolean requiresFinalSynthesisValidation(
      Optional<String> requiredTool, LlmResponse response, List<AgentToolResult> results) {
    return requiresHistorySynthesis(requiredTool)
        && response != null
        && !satisfiesProfileContract(response, results);
  }

  LlmResponse requireExactToolCall(
      LlmResponse response, String toolName, Optional<String> expectedNick)
      throws AgentRoutingException {
    if (response == null
        || response.toolCalls().size() != 1
        || !toolName.equals(response.toolCalls().getFirst().name())
        || !matchesTarget(response.toolCalls().getFirst(), expectedNick)) {
      throw new AgentRoutingException(
          "Agent did not call exactly the required fresh-data tool: " + toolName);
    }
    return response;
  }

  boolean isExactToolCall(LlmResponse response, String toolName, Optional<String> expectedNick) {
    try {
      requireExactToolCall(response, toolName, expectedNick);
      return true;
    } catch (AgentRoutingException exception) {
      return false;
    }
  }

  boolean matchesTarget(LlmToolCall call, Optional<String> expectedNick) {
    if (expectedNick.isEmpty() || !AgentFreshnessPolicy.USER_MESSAGE_HISTORY.equals(call.name()))
      return true;
    try {
      JsonElement nick = JsonParser.parseString(call.arguments()).getAsJsonObject().get("nick");
      return nick != null
          && nick.isJsonPrimitive()
          && expectedNick.orElseThrow().equalsIgnoreCase(nick.getAsString().trim());
    } catch (JsonParseException | IllegalStateException exception) {
      return false;
    }
  }

  boolean repeatsPreviousAssistant(LlmResponse response, List<LlmMessage> history) {
    if (response == null
        || !response.toolCalls().isEmpty()
        || response.content() == null
        || response.content().isBlank()) {
      return false;
    }
    return AgentMessageHistory.latestContent(history, "assistant")
        .map(previous -> response.content().strip().equals(previous.strip()))
        .orElse(false);
  }

  LlmResponse requireFreshSynthesis(LlmResponse response, List<LlmMessage> history)
      throws AgentRoutingException {
    if (response == null) {
      throw new AgentRoutingException("Agent returned no response");
    }
    if (!response.toolCalls().isEmpty()) {
      throw new AgentRoutingException(
          "Agent returned a tool call instead of a fresh history synthesis");
    }
    if (repeatsPreviousAssistant(response, history)) {
      throw new AgentRoutingException(
          "Agent reused the previous answer after a fresh history lookup");
    }
    return response;
  }
}
