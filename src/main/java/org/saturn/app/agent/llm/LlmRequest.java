package org.saturn.app.agent.llm;

import com.google.gson.JsonObject;
import java.util.List;

public record LlmRequest(
    List<LlmMessage> messages,
    List<JsonObject> tools,
    boolean bypassPromptCache,
    JsonObject responseFormat) {
  public LlmRequest(List<LlmMessage> messages, List<JsonObject> tools) {
    this(messages, tools, false, null);
  }

  public LlmRequest(List<LlmMessage> messages, List<JsonObject> tools, boolean bypassPromptCache) {
    this(messages, tools, bypassPromptCache, null);
  }

  public LlmRequest {
    messages = List.copyOf(messages);
    tools = List.copyOf(tools);
  }

  public static LlmRequest withoutPromptCache(List<LlmMessage> messages, List<JsonObject> tools) {
    return new LlmRequest(messages, tools, true);
  }
}
