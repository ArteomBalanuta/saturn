package org.saturn.app.agent.tool.execution;

import com.google.gson.JsonObject;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolResult;

/** Observational seam; implementations cannot invoke the tool directly. */
@FunctionalInterface
public interface AgentToolExecutionMiddleware {
  AgentToolResult execute(
      AgentToolExecutionContext context,
      AgentTool tool,
      JsonObject arguments,
      Continuation continuation)
      throws Exception;

  @FunctionalInterface
  interface Continuation {
    AgentToolResult invoke() throws Exception;
  }
}
