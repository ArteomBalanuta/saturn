package org.saturn.app.agent;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Runs one validated tool under its configured timeout on request-local virtual threads. */
final class AgentToolInvoker implements AutoCloseable {
  private final ExecutorService executor;

  AgentToolInvoker(ExecutorService executor) {
    this.executor = executor;
  }

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

  @Override
  public void close() {
    executor.shutdownNow();
  }
}
