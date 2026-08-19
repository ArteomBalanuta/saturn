package org.saturn.app.agent.turn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentExecutionLimits;

class AgentExecutionStateTest {
  @Test
  void independentlyCapsStepsAndToolCallsForOneRequest() {
    AgentExecutionState state =
        new AgentExecutionState(new AgentExecutionLimits(2, 3, Duration.ofSeconds(1)));

    assertTrue(state.advanceStep());
    assertTrue(state.advanceStep());
    assertFalse(state.advanceStep());
    assertTrue(state.reserveToolCalls(2));
    assertFalse(state.reserveToolCalls(2));
  }
}
