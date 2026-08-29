package org.saturn.app.agent.llm;

import java.util.List;

/**
 * Represents one message in a language-model conversation.
 *
 * @param role protocol role of the message
 * @param content textual message content
 * @param toolCalls tool calls carried by an assistant message
 * @param toolCallId identifier referenced by a tool-result message
 */
public record LlmMessage(
    String role, String content, List<LlmToolCall> toolCalls, String toolCallId) {
  /**
   * Implements the {@code system} operation for this agent component.
   *
   * @param content input argument used by this operation
   * @return the operation result
   */
  public static LlmMessage system(String content) {
    return new LlmMessage("system", content, List.of(), null);
  }

  /**
   * Implements the {@code user} operation for this agent component.
   *
   * @param content input argument used by this operation
   * @return the operation result
   */
  public static LlmMessage user(String content) {
    return new LlmMessage("user", content, List.of(), null);
  }

  /**
   * Implements the {@code assistant} operation for this agent component.
   *
   * @param content input argument used by this operation
   * @param toolCalls input argument used by this operation
   * @return the operation result
   */
  public static LlmMessage assistant(String content, List<LlmToolCall> toolCalls) {
    return new LlmMessage("assistant", content, List.copyOf(toolCalls), null);
  }

  /**
   * Implements the {@code tool} operation for this agent component.
   *
   * @param toolCallId input argument used by this operation
   * @param content input argument used by this operation
   * @return the operation result
   */
  public static LlmMessage tool(String toolCallId, String content) {
    return new LlmMessage("tool", content, List.of(), toolCallId);
  }
}
