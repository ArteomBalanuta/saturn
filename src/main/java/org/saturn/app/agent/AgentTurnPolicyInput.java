package org.saturn.app.agent;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmResponse;

/** Immutable inputs shared by ordered turn policies. */
record AgentTurnPolicyInput(
    LlmResponse response,
    List<LlmMessage> messages,
    List<JsonObject> definitions,
    AgentCommandProseGuard commandProseGuard,
    AgentTurnState turnState,
    String prompt,
    String correlationId,
    Optional<String> requiredFreshTool) {
  AgentTurnPolicyInput(
      LlmResponse response,
      List<LlmMessage> messages,
      List<JsonObject> definitions,
      AgentCommandProseGuard commandProseGuard,
      AgentTurnState turnState,
      String prompt,
      String correlationId) {
    this(
        response,
        messages,
        definitions,
        commandProseGuard,
        turnState,
        prompt,
        correlationId,
        Optional.empty());
  }

  AgentTurnPolicyInput {
    Objects.requireNonNull(response, "response");
    Objects.requireNonNull(messages, "messages");
    Objects.requireNonNull(definitions, "definitions");
    Objects.requireNonNull(commandProseGuard, "commandProseGuard");
    Objects.requireNonNull(turnState, "turnState");
    Objects.requireNonNull(prompt, "prompt");
    Objects.requireNonNull(correlationId, "correlationId");
    Objects.requireNonNull(requiredFreshTool, "requiredFreshTool");
  }
}

/** Explicit outcome from one ordered turn policy. */
record AgentTurnPolicyResult(
    LlmResponse response, boolean correctionUsed, boolean continuePolicyEvaluation) {
  AgentTurnPolicyResult(LlmResponse response, boolean correctionUsed) {
    this(response, correctionUsed, true);
  }

  AgentTurnPolicyResult {
    Objects.requireNonNull(response, "response");
  }

  static AgentTurnPolicyResult stop(LlmResponse response) {
    return new AgentTurnPolicyResult(response, false, false);
  }
}
