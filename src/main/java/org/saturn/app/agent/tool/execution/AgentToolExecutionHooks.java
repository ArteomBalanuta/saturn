package org.saturn.app.agent.tool.execution;

import java.util.List;

/** Immutable request-local execution extension point. */
public record AgentToolExecutionHooks(
    List<AgentToolExecutionMiddleware> middleware, List<AgentToolExecutionObserver> observers) {
  public AgentToolExecutionHooks {
    middleware = List.copyOf(middleware == null ? List.of() : middleware);
    observers = List.copyOf(observers == null ? List.of() : observers);
  }

  /**
   * Implements the {@code empty} operation for this agent component.
   *
   * @return the operation result
   */
  public static AgentToolExecutionHooks empty() {
    return new AgentToolExecutionHooks(List.of(), List.of());
  }
}
