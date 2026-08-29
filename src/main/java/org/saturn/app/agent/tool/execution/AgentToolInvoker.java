package org.saturn.app.agent.tool.execution;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolResult;

/** Runs one validated tool under its configured timeout on request-local virtual threads. */
final class AgentToolInvoker implements AutoCloseable {
  private final ExecutorService executor;

  AgentToolInvoker(ExecutorService executor) {
    this.executor = executor;
  }

  /**
   * Invokes a tool with its arguments, deadline, and cancellation behavior.
   *
   * @param tool the tool input; null handling follows the validation performed by this declaration
   * @param context the context input; null handling follows the validation performed by this
   *     declaration
   * @param arguments the arguments input; null handling follows the validation performed by this
   *     declaration
   * @param timeout the timeout input; null handling follows the validation performed by this
   *     declaration
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  AgentToolResult invoke(
      AgentTool tool, AgentContext context, JsonObject arguments, Duration timeout)
      throws InterruptedException, ExecutionException, TimeoutException {
    Future<AgentToolResult> future = executor.submit(() -> tool.execute(context, arguments));
    try {
      AgentToolResult result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (result == null) {
        throw new IllegalStateException("Tool returned no result");
      }
      return result;
    } catch (TimeoutException exception) {
      future.cancel(true);
      throw exception;
    }
  }

  /** Implements the {@code close} operation for this agent component. */
  @Override
  public void close() {
    executor.shutdownNow();
  }
}
