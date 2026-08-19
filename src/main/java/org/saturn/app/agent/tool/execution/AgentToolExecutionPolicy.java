package org.saturn.app.agent.tool.execution;

import org.saturn.app.agent.api.AgentToolDescriptor;

/**
 * Classifies tool metadata into the scheduler's safe ordering categories.
 *
 * <p>Only independent, idempotent reads may run concurrently. Every action and dependency remains
 * an order barrier.
 */
final class AgentToolExecutionPolicy {
  AgentToolExecutionMode classify(AgentToolDescriptor descriptor) {
    if (!descriptor.isReadOnly()) {
      return AgentToolExecutionMode.SEQUENTIAL_ACTION;
    }
    if (!descriptor.isIdempotent() || !descriptor.requiredSuccessfulTools().isEmpty()) {
      return AgentToolExecutionMode.SEQUENTIAL_DEPENDENT_READ;
    }
    return AgentToolExecutionMode.PARALLEL_READ;
  }

  boolean isParallelSafe(AgentToolDescriptor descriptor) {
    return classify(descriptor) == AgentToolExecutionMode.PARALLEL_READ;
  }
}
