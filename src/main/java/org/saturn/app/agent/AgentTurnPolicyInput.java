package org.saturn.app.agent;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
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
    String correlationId) {
  AgentTurnPolicyInput {
    Objects.requireNonNull(response, "response");
    Objects.requireNonNull(messages, "messages");
    Objects.requireNonNull(definitions, "definitions");
    Objects.requireNonNull(commandProseGuard, "commandProseGuard");
    Objects.requireNonNull(turnState, "turnState");
    Objects.requireNonNull(prompt, "prompt");
    Objects.requireNonNull(correlationId, "correlationId");
  }
}

/** Explicit outcome from one ordered turn policy. */
record AgentTurnPolicyResult(LlmResponse response, boolean correctionUsed) {
  AgentTurnPolicyResult {
    Objects.requireNonNull(response, "response");
  }
}
