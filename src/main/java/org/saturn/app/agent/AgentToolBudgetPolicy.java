package org.saturn.app.agent;

/** Owns per-turn tool-call reservation and the exhausted-budget transition. */
final class AgentToolBudgetPolicy {
  Result reserve(int requestedCalls, AgentTurnState turnState) {
    if (requestedCalls <= 0) {
      throw new IllegalArgumentException("requestedCalls must be positive");
    }
    if (turnState.reserveToolCalls(requestedCalls)) {
      return new Result(true, false);
    }
    turnState.disableTools();
    return new Result(false, true);
  }

  record Result(boolean executeTools, boolean finalizeWithoutTools) {}
}
