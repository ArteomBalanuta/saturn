package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AgentTurnStateTest {
  @Test
  void keepsRequestScopedBudgetsCorrectionsAndToolOutcomesTogether() {
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(1, 2, Duration.ofSeconds(1)));

    assertTrue(state.advanceStep());
    assertFalse(state.advanceStep());
    assertTrue(state.reserveToolCalls(2));
    assertFalse(state.reserveToolCalls(1));
    assertFalse(state.commandCorrectionUsed());
    state.markCommandCorrectionUsed();
    state.recordSuccessfulCommand("weather");
    state.recordFailedCommand("ping");
    state.recordSuccessfulTool("user_message_history");

    assertTrue(state.commandCorrectionUsed());
    assertTrue(state.hasSuccessfulCommand("weather"));
    assertTrue(state.hasSuccessfulTool("user_message_history"));
    assertTrue(state.hasSuccessfulCommands());
    assertTrue(state.successfulCommands().contains("weather"));
    assertTrue(state.failedCommands().contains("ping"));
    assertTrue(state.successfulTools().contains("user_message_history"));
  }
}
