package org.saturn.app.agent;

/** Request-local counter state for a single agent turn. */
final class AgentExecutionState {
  private final AgentExecutionLimits limits;
  private int steps;
  private int toolCalls;

  AgentExecutionState(AgentExecutionLimits limits) {
    this.limits = limits;
  }

  boolean advanceStep() {
    if (steps >= limits.maxSteps()) {
      return false;
    }
    steps++;
    return true;
  }

  boolean reserveToolCalls(int requestedCalls) {
    if (requestedCalls < 0 || toolCalls + requestedCalls > limits.maxToolCallsPerTurn()) {
      return false;
    }
    toolCalls += requestedCalls;
    return true;
  }
}
