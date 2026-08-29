package org.saturn.app.agent.llm;

import com.google.gson.JsonObject;
import java.util.List;
import org.saturn.app.agent.routing.AgentContextProjection;

/** Represents a request submitted to a language-model client. */
public record LlmRequest(
    List<LlmMessage> messages,
    List<JsonObject> tools,
    boolean bypassPromptCache,
    JsonObject responseFormat,
    AgentContextProjection projection) {
  public LlmRequest(List<LlmMessage> messages, List<JsonObject> tools) {
    this(messages, tools, false, null, null);
  }

  public LlmRequest(List<LlmMessage> messages, List<JsonObject> tools, boolean bypassPromptCache) {
    this(messages, tools, bypassPromptCache, null, null);
  }

  public LlmRequest(
      List<LlmMessage> messages,
      List<JsonObject> tools,
      boolean bypassPromptCache,
      JsonObject responseFormat) {
    this(messages, tools, bypassPromptCache, responseFormat, null);
  }

  public LlmRequest(
      List<LlmMessage> messages, List<JsonObject> tools, AgentContextProjection projection) {
    this(messages, tools, false, null, projection);
  }

  public LlmRequest {
    messages = List.copyOf(messages);
    tools = List.copyOf(tools);
    if (projection != null
        && !projection
            .fingerprint()
            .equals(org.saturn.app.agent.routing.AgentMessageProjector.fingerprintOf(messages))) {
      throw new IllegalArgumentException("projection fingerprint does not match request messages");
    }
  }

  public static LlmRequest withoutPromptCache(List<LlmMessage> messages, List<JsonObject> tools) {
    return new LlmRequest(messages, tools, true);
  }
}
