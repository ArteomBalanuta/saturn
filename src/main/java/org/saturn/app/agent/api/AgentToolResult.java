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

  public AgentToolResult(String callId, String toolName, String content, boolean isError) {
    this(callId, toolName, content, isError, isError ? "TOOL_EXECUTION_FAILED" : null);
  }

  public static AgentToolResult success(String toolName, Object value) {
    String content = value instanceof String text ? text : GSON.toJson(value);
    return new AgentToolResult(null, toolName, content, false, null);
  }

  public static AgentToolResult error(String callId, String toolName, String message) {
    return error(callId, toolName, "TOOL_EXECUTION_FAILED", message);
  }

  public static AgentToolResult error(
      String callId, String toolName, String errorCode, String message) {
    return new AgentToolResult(callId, toolName, message, true, errorCode);
  }

  public AgentToolResult withCallId(String id) {
    return new AgentToolResult(id, toolName, content, isError, errorCode);
  }

  public String envelopeJson() {
    return isError
        ? ToolResponseEnvelope.error(errorCode, content).toJson()
        : ToolResponseEnvelope.success(content).toJson();
  }
}
