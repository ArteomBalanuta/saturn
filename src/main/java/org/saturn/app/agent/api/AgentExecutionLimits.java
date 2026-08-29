package org.saturn.app.agent.api;

import java.time.Duration;
import java.util.Objects;
import org.saturn.app.agent.config.AgentConfig;

/** Immutable per-request loop and default tool-timeout limits derived from agent configuration. */
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

  /**
   * Implements the {@code from} operation for this agent component.
   *
   * @param config input argument used by this operation
   * @return the operation result
   */
  public static AgentExecutionLimits from(AgentConfig config) {
    return new AgentExecutionLimits(
        config.maxSteps(), config.maxToolCallsPerTurn(), config.toolTimeout());
  }
}
