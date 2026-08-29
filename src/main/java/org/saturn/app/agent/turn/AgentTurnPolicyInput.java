package org.saturn.app.agent.turn;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.routing.AgentCommandProseGuard;

/**
 * Immutable inputs shared by ordered turn policies.
 *
 * @param response current provider response
 * @param messages request-local projected messages
 * @param definitions available tool definitions
 * @param commandProseGuard command-provenance guard
 * @param turnState mutable request-local turn state
 * @param prompt original prompt
 * @param correlationId request correlation identifier
 * @param requiredFreshTool tool required by freshness policy, when any
 */
public record AgentTurnPolicyInput(
    LlmResponse response,
    List<LlmMessage> messages,
    List<JsonObject> definitions,
    AgentCommandProseGuard commandProseGuard,
    AgentTurnState turnState,
    String prompt,
    String correlationId,
    Optional<String> requiredFreshTool) {
  /**
   * Implements the {@code AgentTurnPolicyInput} operation for this agent component.
   *
   * @param response input argument used by this operation
   * @param messages input argument used by this operation
   * @param definitions input argument used by this operation
   * @param commandProseGuard input argument used by this operation
   * @param turnState input argument used by this operation
   * @param prompt input argument used by this operation
   * @param correlationId input argument used by this operation
   */
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

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param response the response input; null handling follows the validation performed by this
   *     declaration
   * @param messages the messages input; null handling follows the validation performed by this
   *     declaration
   * @param definitions the definitions input; null handling follows the validation performed by
   *     this declaration
   * @param commandProseGuard the commandProseGuard input; null handling follows the validation
   *     performed by this declaration
   * @param turnState the turnState input; null handling follows the validation performed by this
   *     declaration
   * @param prompt the prompt input; null handling follows the validation performed by this
   *     declaration
   * @param correlationId the correlationId input; null handling follows the validation performed by
   *     this declaration
   * @param requiredFreshTool the requiredFreshTool input; null handling follows the validation
   *     performed by this declaration
   */
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
