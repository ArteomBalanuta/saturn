package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentExecutionLimits;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.agent.config.AgentConfigLoader;

class AgentExecutionLimitsTest {
  @Test
  void acceptsPositiveTurnBoundsAndPreservesConfiguredTimeout() {
    Duration timeout = Duration.ofMillis(250);

    AgentExecutionLimits limits = new AgentExecutionLimits(3, 4, timeout);

    assertEquals(3, limits.maxSteps());
    assertEquals(4, limits.maxToolCallsPerTurn());
    assertEquals(timeout, limits.toolTimeout());
  }

  @Test
  void rejectsNonPositiveStepAndToolCallBounds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AgentExecutionLimits(0, 1, Duration.ofSeconds(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AgentExecutionLimits(1, 0, Duration.ofSeconds(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AgentExecutionLimits(-1, 1, Duration.ofSeconds(1)));
  }

  @Test
  void rejectsNullAndNonPositiveTimeouts() {
    assertThrows(NullPointerException.class, () -> new AgentExecutionLimits(1, 1, null));
    assertThrows(
        IllegalArgumentException.class, () -> new AgentExecutionLimits(1, 1, Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AgentExecutionLimits(1, 1, Duration.ofSeconds(-1)));
  }

  @Test
  void derivesAllValuesFromAgentConfiguration() {
    AgentConfig config = AgentConfigLoader.load(null, Map.of());

    AgentExecutionLimits limits = AgentExecutionLimits.from(config);

    assertEquals(config.maxSteps(), limits.maxSteps());
    assertEquals(config.maxToolCallsPerTurn(), limits.maxToolCallsPerTurn());
    assertEquals(config.toolTimeout(), limits.toolTimeout());
  }

  @Test
  void rejectsNullConfiguration() {
    assertThrows(NullPointerException.class, () -> AgentExecutionLimits.from(null));
  }
}
