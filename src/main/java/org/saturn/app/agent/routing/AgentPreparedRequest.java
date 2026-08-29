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

  AgentPreparedRequest {
    messages = List.copyOf(messages);
    definitions = definitions.stream().map(JsonObject::deepCopy).toList();
    requiredFreshTool = requiredFreshTool == null ? Optional.empty() : requiredFreshTool;
    requiredFreshNick = requiredFreshNick == null ? Optional.empty() : requiredFreshNick;
  }
}
