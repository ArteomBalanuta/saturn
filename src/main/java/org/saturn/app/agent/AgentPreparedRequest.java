package org.saturn.app.agent;

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
    Optional<String> requiredFreshNick) {
  AgentPreparedRequest {
    messages = List.copyOf(messages);
    definitions = List.copyOf(definitions);
    requiredFreshTool = requiredFreshTool == null ? Optional.empty() : requiredFreshTool;
    requiredFreshNick = requiredFreshNick == null ? Optional.empty() : requiredFreshNick;
  }
}
