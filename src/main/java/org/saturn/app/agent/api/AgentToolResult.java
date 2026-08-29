package org.saturn.app.agent.api;

import com.google.gson.Gson;

/** Represents the observable result of an agent tool invocation. */
public record AgentToolResult(
    String callId, String toolName, String content, boolean isError, String errorCode) {
  private static final Gson GSON = new Gson();

  public AgentToolResult {
    if (isError && (errorCode == null || errorCode.isBlank())) {
      errorCode = "TOOL_EXECUTION_FAILED";
    }
  }

  /**
   * Implements the {@code AgentToolResult} operation for this agent component.
   *
   * @param callId input argument used by this operation
   * @param toolName input argument used by this operation
   * @param content input argument used by this operation
   * @param isError input argument used by this operation
   */
  public AgentToolResult(String callId, String toolName, String content, boolean isError) {
    this(callId, toolName, content, isError, isError ? "TOOL_EXECUTION_FAILED" : null);
  }

  /**
   * Implements the {@code success} operation for this agent component.
   *
   * @param toolName input argument used by this operation
   * @param value input argument used by this operation
   * @return the operation result
   */
  public static AgentToolResult success(String toolName, Object value) {
    String content = value instanceof String text ? text : GSON.toJson(value);
    return new AgentToolResult(null, toolName, content, false, null);
  }

  /**
   * Implements the {@code error} operation for this agent component.
   *
   * @param callId input argument used by this operation
   * @param toolName input argument used by this operation
   * @param message input argument used by this operation
   * @return the operation result
   */
  public static AgentToolResult error(String callId, String toolName, String message) {
    return error(callId, toolName, "TOOL_EXECUTION_FAILED", message);
  }

  /**
   * Implements the {@code error} operation for this agent component.
   *
   * @param callId input argument used by this operation
   * @param toolName input argument used by this operation
   * @param errorCode input argument used by this operation
   * @param message input argument used by this operation
   * @return the operation result
   */
  public static AgentToolResult error(
      String callId, String toolName, String errorCode, String message) {
    return new AgentToolResult(callId, toolName, message, true, errorCode);
  }

  /**
   * Implements the {@code withCallId} operation for this agent component.
   *
   * @param id input argument used by this operation
   * @return the operation result
   */
  public AgentToolResult withCallId(String id) {
    return new AgentToolResult(id, toolName, content, isError, errorCode);
  }

  /**
   * Implements the {@code envelopeJson} operation for this agent component.
   *
   * @return the operation result
   */
  public String envelopeJson() {
    return isError
        ? ToolResponseEnvelope.error(errorCode, content).toJson()
        : ToolResponseEnvelope.success(content).toJson();
  }
}
