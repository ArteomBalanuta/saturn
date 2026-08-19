package org.saturn.app.agent.turn;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.routing.AgentCommandProseGuard;

/** Immutable inputs shared by ordered turn policies. */
public record AgentTurnPolicyInput(
    LlmResponse response,
    List<LlmMessage> messages,
    List<JsonObject> definitions,
    AgentCommandProseGuard commandProseGuard,
    AgentTurnState turnState,
    String prompt,
    String correlationId,
    Optional<String> requiredFreshTool) {
  public AgentTurnPolicyInput(
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

  public AgentTurnPolicyInput {
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
