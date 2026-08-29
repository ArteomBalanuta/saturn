package org.saturn.app.agent.tool.execution;

import org.saturn.app.agent.api.AgentToolResult;

/** Receives a completed result without access to an invokable handler. */
@FunctionalInterface
public interface AgentToolExecutionObserver {
  void onOutcome(AgentToolExecutionContext context, AgentToolResult result);
}
