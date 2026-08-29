package org.saturn.app.agent.routing;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import org.saturn.app.agent.llm.LlmMessage;

/** Immutable provider request state prepared before the session tool loop begins. */
record AgentPreparedRequest(
    List<LlmMessage> messages,
    List<JsonObject> definitions,
    String contextualizedPrompt,
    Optional<String> requiredFreshTool,
    Optional<String> requiredFreshNick,
    AgentRequestKind requestKind,
    int serializedChars,
    int estimatedTokens,
    int budgetChars,
    boolean pruned,
    boolean overflow,
    int removedUnits,
    String contextFingerprint) {
  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param messages the messages input; null handling follows the validation performed by this
   *     declaration
   * @param definitions the definitions input; null handling follows the validation performed by
   *     this declaration
   * @param contextualizedPrompt the contextualizedPrompt input; null handling follows the
   *     validation performed by this declaration
   * @param requiredFreshTool the requiredFreshTool input; null handling follows the validation
   *     performed by this declaration
   * @param requiredFreshNick the requiredFreshNick input; null handling follows the validation
   *     performed by this declaration
   */
  AgentPreparedRequest(
      List<LlmMessage> messages,
      List<JsonObject> definitions,
      String contextualizedPrompt,
      Optional<String> requiredFreshTool,
      Optional<String> requiredFreshNick) {
    this(
        messages,
        definitions,
        contextualizedPrompt,
        requiredFreshTool,
        requiredFreshNick,
        AgentRequestKind.UNCLASSIFIED);
  }

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param messages the messages input; null handling follows the validation performed by this
   *     declaration
   * @param definitions the definitions input; null handling follows the validation performed by
   *     this declaration
   * @param contextualizedPrompt the contextualizedPrompt input; null handling follows the
   *     validation performed by this declaration
   * @param requiredFreshTool the requiredFreshTool input; null handling follows the validation
   *     performed by this declaration
   * @param requiredFreshNick the requiredFreshNick input; null handling follows the validation
   *     performed by this declaration
   * @param requestKind the requestKind input; null handling follows the validation performed by
   *     this declaration
   */
  AgentPreparedRequest(
      List<LlmMessage> messages,
      List<JsonObject> definitions,
      String contextualizedPrompt,
      Optional<String> requiredFreshTool,
      Optional<String> requiredFreshNick,
      AgentRequestKind requestKind) {
    this(
        messages,
        definitions,
        contextualizedPrompt,
        requiredFreshTool,
        requiredFreshNick,
        requestKind,
        messages.stream().mapToInt(m -> String.valueOf(m.content()).length()).sum(),
        0,
        0,
        false,
        false,
        0,
        "");
  }

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param messages the messages input; null handling follows the validation performed by this
   *     declaration
   * @param definitions the definitions input; null handling follows the validation performed by
   *     this declaration
   * @param contextualizedPrompt the contextualizedPrompt input; null handling follows the
   *     validation performed by this declaration
   * @param requiredFreshTool the requiredFreshTool input; null handling follows the validation
   *     performed by this declaration
   * @param requiredFreshNick the requiredFreshNick input; null handling follows the validation
   *     performed by this declaration
   * @param requestKind the requestKind input; null handling follows the validation performed by
   *     this declaration
   * @param projection the projection input; null handling follows the validation performed by this
   *     declaration
   */
  AgentPreparedRequest(
      List<LlmMessage> messages,
      List<JsonObject> definitions,
      String contextualizedPrompt,
      Optional<String> requiredFreshTool,
      Optional<String> requiredFreshNick,
      AgentRequestKind requestKind,
      AgentContextProjection projection) {
    this(
        messages,
        definitions,
        contextualizedPrompt,
        requiredFreshTool,
        requiredFreshNick,
        requestKind,
        projection.serializedChars(),
        projection.estimatedTokens(),
        projection.budgetChars(),
        projection.pruned(),
        projection.overflow(),
        projection.removedUnits(),
        projection.fingerprint());
  }

  /**
   * Implements the {@code projection} operation for this agent component.
   *
   * @return the operation result
   */
  public AgentContextProjection projection() {
    return new AgentContextProjection(
        messages,
        serializedChars,
        estimatedTokens,
        budgetChars,
        pruned,
        overflow,
        removedUnits,
        contextFingerprint);
  }

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param messages the messages input; null handling follows the validation performed by this
   *     declaration
   * @param definitions the definitions input; null handling follows the validation performed by
   *     this declaration
   * @param contextualizedPrompt the contextualizedPrompt input; null handling follows the
   *     validation performed by this declaration
   * @param requiredFreshTool the requiredFreshTool input; null handling follows the validation
   *     performed by this declaration
   * @param requiredFreshNick the requiredFreshNick input; null handling follows the validation
   *     performed by this declaration
   * @param requestKind the requestKind input; null handling follows the validation performed by
   *     this declaration
   * @param serializedChars the serializedChars input; null handling follows the validation
   *     performed by this declaration
   * @param estimatedTokens the estimatedTokens input; null handling follows the validation
   *     performed by this declaration
   * @param budgetChars the budgetChars input; null handling follows the validation performed by
   *     this declaration
   * @param pruned the pruned input; null handling follows the validation performed by this
   *     declaration
   * @param overflow the overflow input; null handling follows the validation performed by this
   *     declaration
   * @param removedUnits the removedUnits input; null handling follows the validation performed by
   *     this declaration
   * @param contextFingerprint the contextFingerprint input; null handling follows the validation
   *     performed by this declaration
   */
  AgentPreparedRequest {
    messages = List.copyOf(messages);
    definitions = definitions.stream().map(JsonObject::deepCopy).toList();
    requiredFreshTool = requiredFreshTool == null ? Optional.empty() : requiredFreshTool;
    requiredFreshNick = requiredFreshNick == null ? Optional.empty() : requiredFreshNick;
  }
}
