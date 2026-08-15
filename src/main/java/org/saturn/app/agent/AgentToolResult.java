package org.saturn.app.agent;

import com.google.gson.Gson;

public record AgentToolResult(String callId, String toolName, String content, boolean isError) {
  private static final Gson GSON = new Gson();

  public static AgentToolResult success(String toolName, Object value) {
    String content = value instanceof String text ? text : GSON.toJson(value);
    return new AgentToolResult(null, toolName, content, false);
  }

  public static AgentToolResult error(String callId, String toolName, String message) {
    return new AgentToolResult(callId, toolName, message, true);
  }

  public AgentToolResult withCallId(String id) {
    return new AgentToolResult(id, toolName, content, isError);
  }
}
