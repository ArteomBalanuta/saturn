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
    if (!descriptor.resourceWrites().isEmpty()
        || !descriptor.isIdempotent()
        || !descriptor.requiredSuccessfulTools().isEmpty()) {
      return AgentToolExecutionMode.SEQUENTIAL_DEPENDENT_READ;
    }
    return AgentToolExecutionMode.PARALLEL_READ;
  }

  AgentToolExecutionMode classify(AgentToolDescriptor descriptor, AgentToolDescriptor previous) {
    AgentToolExecutionMode mode = classify(descriptor);
    if (mode != AgentToolExecutionMode.PARALLEL_READ || previous == null) {
      return mode;
    }
    if (previous.resourceReads().isEmpty() && previous.resourceWrites().isEmpty()
        || descriptor.resourceReads().isEmpty() && descriptor.resourceWrites().isEmpty()) {
      return mode;
    }
    return conflicts(previous, descriptor)
        ? AgentToolExecutionMode.SEQUENTIAL_DEPENDENT_READ
        : mode;
  }

  private boolean conflicts(AgentToolDescriptor first, AgentToolDescriptor second) {
    if (first.resourceReads().isEmpty() && first.resourceWrites().isEmpty()
        || second.resourceReads().isEmpty() && second.resourceWrites().isEmpty()) {
      return true;
    }
    if (first.resourceWrites().isEmpty() && second.resourceWrites().isEmpty()) {
      return false;
    }
    return !java.util.Collections.disjoint(first.resourceWrites(), second.resourceReads())
        || !java.util.Collections.disjoint(first.resourceReads(), second.resourceWrites())
        || !java.util.Collections.disjoint(first.resourceWrites(), second.resourceWrites());
  }

  boolean isParallelSafe(AgentToolDescriptor descriptor, AgentToolDescriptor previous) {
    return classify(descriptor, previous) == AgentToolExecutionMode.PARALLEL_READ;
  }

  boolean compatible(AgentScheduledToolCall first, AgentScheduledToolCall second) {
    if (!first.hasKnownResources() && !second.hasKnownResources()) {
      return true;
    }
    if (!first.hasKnownResources() || !second.hasKnownResources()) {
      return false;
    }
    return java.util.Collections.disjoint(first.resourceWrites(), second.resourceReads())
        && java.util.Collections.disjoint(first.resourceReads(), second.resourceWrites())
        && java.util.Collections.disjoint(first.resourceWrites(), second.resourceWrites());
  }

  boolean isParallelSafe(AgentToolDescriptor descriptor) {
    return classify(descriptor) == AgentToolExecutionMode.PARALLEL_READ;
  }
}
