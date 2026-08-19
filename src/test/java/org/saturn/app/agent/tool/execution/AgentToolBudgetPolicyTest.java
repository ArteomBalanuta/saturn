package org.saturn.app.agent.tool.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentExecutionLimits;
import org.saturn.app.agent.turn.AgentTurnState;

class AgentToolBudgetPolicyTest {
  @Test
  void reservesCallsWhileTheTurnBudgetAllowsThem() {
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 2, Duration.ofSeconds(1)));

    AgentToolBudgetPolicy.Result result = new AgentToolBudgetPolicy().reserve(2, state);

    assertTrue(result.executeTools());
    assertFalse(result.finalizeWithoutTools());
    assertTrue(state.toolsEnabled());
  }

  @Test
  void disablesToolsAndRequestsFinalizationWhenTheBudgetIsExhausted() {
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 1, Duration.ofSeconds(1)));

    AgentToolBudgetPolicy.Result result = new AgentToolBudgetPolicy().reserve(2, state);

    assertFalse(result.executeTools());
    assertTrue(result.finalizeWithoutTools());
    assertFalse(state.toolsEnabled());
  }
}
