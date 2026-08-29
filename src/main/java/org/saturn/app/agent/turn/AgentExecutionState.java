package org.saturn.app.agent.turn;

import org.saturn.app.agent.api.AgentExecutionLimits;

/** Request-local counter state for a single agent turn. */
final class AgentExecutionState {
  private final AgentExecutionLimits limits;
  private int steps;
  private int toolCalls;

  AgentExecutionState(AgentExecutionLimits limits) {
    this.limits = limits;
  }

  /** Advances the bounded execution state by one step. */
  boolean advanceStep() {
    if (steps >= limits.maxSteps()) {
      return false;
    }
    steps++;
    return true;
  }

  /**
   * Reserves tool-call budget for this turn and rejects an over-budget request.
   *
   * @param requestedCalls the requestedCalls input; null handling follows the validation performed
   *     by this declaration
   */
  boolean reserveToolCalls(int requestedCalls) {
    if (requestedCalls < 0 || toolCalls + requestedCalls > limits.maxToolCallsPerTurn()) {
      return false;
    }
    toolCalls += requestedCalls;
    return true;
  }
}
