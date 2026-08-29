package org.saturn.app.agent.tool.execution;

import org.saturn.app.agent.api.AgentToolDescriptor;

/**
 * Classifies tool metadata into the scheduler's safe ordering categories.
 *
 * <p>Only independent, idempotent reads may run concurrently. Every action and dependency remains
 * an order barrier.
 */
final class AgentToolExecutionPolicy {
  /**
   * Classifies one descriptor without a preceding call.
   *
   * @param descriptor descriptor to classify
   * @return the ordering mode required by its effect, idempotence, and prerequisites
   */
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

  /**
   * Classifies a descriptor in relation to the immediately preceding descriptor.
   *
   * @param descriptor descriptor to classify
   * @param previous preceding descriptor, or {@code null} when there is none
   * @return the safe ordering mode, including a barrier for conflicting resources
   */
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

  /**
   * Implements the {@code conflicts} operation for this agent component.
   *
   * @param first input argument used by this operation
   * @param second input argument used by this operation
   * @return the operation result
   */
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

  /**
   * Returns whether the supplied descriptor can run concurrently with the preceding call.
   *
   * @param descriptor descriptor to classify
   * @param previous preceding scheduled call descriptor
   * @return {@code true} only for an independent parallel-read classification
   */
  boolean isParallelSafe(AgentToolDescriptor descriptor, AgentToolDescriptor previous) {
    return classify(descriptor, previous) == AgentToolExecutionMode.PARALLEL_READ;
  }

  /**
   * Tests whether two scheduled calls have non-conflicting known resources.
   *
   * @param first first scheduled call
   * @param second second scheduled call
   * @return {@code true} when the calls may share a parallel batch
   */
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

  /**
   * Returns whether a descriptor is an independent, idempotent read.
   *
   * @param descriptor descriptor to inspect
   * @return {@code true} when no ordering barrier is required
   */
  boolean isParallelSafe(AgentToolDescriptor descriptor) {
    return classify(descriptor) == AgentToolExecutionMode.PARALLEL_READ;
  }
}
