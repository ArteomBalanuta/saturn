package org.saturn.app.agent.tool.execution;

import com.google.gson.JsonObject;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolResult;

/** Observational seam; implementations cannot invoke the tool directly. */
@FunctionalInterface
public interface AgentToolExecutionMiddleware {
  /**
   * Observes or wraps one validated tool execution.
   *
   * @param context immutable execution context
   * @param tool tool selected for execution
   * @param arguments validated tool arguments
   * @param continuation continuation that performs the actual invocation
   * @return the execution result
   * @throws Exception if the middleware or continuation cannot complete
   */
  AgentToolResult execute(
      AgentToolExecutionContext context,
      AgentTool tool,
      JsonObject arguments,
      Continuation continuation)
      throws Exception;

  @FunctionalInterface
  /**
   * Defines the interface {@code Continuation} in the Saturn agent runtime.
   *
   * <p>This type is part of the source-compatible agent boundary; validation and failure behavior
   * are retained by its implementation.
   */
  interface Continuation {
    /**
     * Performs the wrapped tool invocation.
     *
     * @return the tool result
     * @throws Exception if invocation fails
     */
    AgentToolResult invoke() throws Exception;
  }
}
