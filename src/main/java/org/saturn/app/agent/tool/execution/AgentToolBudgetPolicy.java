package org.saturn.app.agent.tool.execution;

import org.saturn.app.agent.turn.AgentTurnState;

/** Owns per-turn tool-call reservation and the exhausted-budget transition. */
public final class AgentToolBudgetPolicy {
  public Result reserve(int requestedCalls, AgentTurnState turnState) {
    if (requestedCalls <= 0) {
      throw new IllegalArgumentException("requestedCalls must be positive");
    }
    if (turnState.reserveToolCalls(requestedCalls)) {
      return new Result(true, false);
    }
    turnState.disableTools();
    return new Result(false, true);
  }

  /** Carries the result value used by the enclosing agent component. */
  public record Result(boolean executeTools, boolean finalizeWithoutTools) {}
}
