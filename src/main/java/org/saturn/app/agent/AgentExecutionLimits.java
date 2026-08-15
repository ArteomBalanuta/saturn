package org.saturn.app.agent;

import java.time.Duration;
import java.util.Objects;

/** Immutable per-turn bounds that prevent a model response from extending execution indefinitely. */
public record AgentExecutionLimits(int maxSteps, int maxToolCallsPerTurn, Duration toolTimeout) {
  public AgentExecutionLimits {
    if (maxSteps <= 0) {
      throw new IllegalArgumentException("maxSteps must be positive");
    }
    if (maxToolCallsPerTurn <= 0) {
      throw new IllegalArgumentException("maxToolCallsPerTurn must be positive");
    }
    Objects.requireNonNull(toolTimeout, "toolTimeout");
    if (toolTimeout.isZero() || toolTimeout.isNegative()) {
      throw new IllegalArgumentException("toolTimeout must be positive");
    }
  }

  public static AgentExecutionLimits from(AgentConfig config) {
    return new AgentExecutionLimits(
        config.maxSteps(), config.maxToolCallsPerTurn(), config.toolTimeout());
  }
}
