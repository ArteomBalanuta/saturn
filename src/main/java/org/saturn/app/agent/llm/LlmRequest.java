package org.saturn.app.agent.llm;

import com.google.gson.JsonObject;
import java.util.List;

public record LlmRequest(List<LlmMessage> messages, List<JsonObject> tools) {
  public LlmRequest {
    messages = List.copyOf(messages);
    tools = List.copyOf(tools);
  }
}
