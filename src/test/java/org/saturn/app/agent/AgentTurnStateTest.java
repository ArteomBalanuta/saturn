package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

  @Test
  void resetsOnlyTheUnverifiedActionFlagAndDisablesToolsPermanently() {
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 2, Duration.ofSeconds(1)));

    assertFalse(state.unverifiedActionChecked());
    state.markUnverifiedActionChecked();
    assertTrue(state.unverifiedActionChecked());
    state.resetUnverifiedActionCheck();
    assertFalse(state.unverifiedActionChecked());
    assertTrue(state.toolsEnabled());
    state.disableTools();
    state.disableTools();
    assertFalse(state.toolsEnabled());
  }

  @Test
  void exposesDefensiveOutcomeSnapshotsAndIdempotentSets() {
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 2, Duration.ofSeconds(1)));
    AgentToolResult result = AgentToolResult.success("weather", "sunny");

    assertTrue(state.recordSuccessfulCommand("weather"));
    assertFalse(state.recordSuccessfulCommand("weather"));
    assertTrue(state.recordSuccessfulTool("weather"));
    assertFalse(state.recordSuccessfulTool("weather"));
    state.recordSuccessfulToolResult(result);
    assertEquals(1, state.successfulToolResults().size());
    assertThrows(
        UnsupportedOperationException.class, () -> state.successfulToolResults().add(result));
    assertThrows(
        UnsupportedOperationException.class, () -> state.successfulCommands().add("mutated"));
    assertThrows(UnsupportedOperationException.class, () -> state.failedCommands().add("mutated"));
    assertThrows(UnsupportedOperationException.class, () -> state.successfulTools().add("mutated"));
  }

  @Test
  void tracksAllCorrectionFlagsIndependently() {
    AgentTurnState state =
        new AgentTurnState(new AgentExecutionLimits(5, 2, Duration.ofSeconds(1)));

    state.markFreshnessCorrectionUsed();
    state.markFreshSynthesisCorrectionUsed();
    state.markCommandCorrectionUsed();

    assertTrue(state.freshnessCorrectionUsed());
    assertTrue(state.freshSynthesisCorrectionUsed());
    assertTrue(state.commandCorrectionUsed());
  }
}
