package org.saturn.app.agent.llm;

import java.util.List;

public record LlmResponse(String content, List<LlmToolCall> toolCalls, String finishReason) {
  public LlmResponse {
    content = content == null ? "" : content;
    toolCalls = List.copyOf(toolCalls);
  }
}
