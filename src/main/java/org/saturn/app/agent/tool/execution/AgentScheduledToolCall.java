package org.saturn.app.agent.tool.execution;

import java.util.Objects;
import java.util.Set;
import org.saturn.app.agent.llm.LlmToolCall;

/** A provider tool call paired with its request-local execution classification. */
record AgentScheduledToolCall(
    LlmToolCall call,
    AgentToolExecutionMode mode,
    Set<String> resourceReads,
    Set<String> resourceWrites) {
  AgentScheduledToolCall {
    call = Objects.requireNonNull(call, "call");
    mode = Objects.requireNonNull(mode, "mode");
    resourceReads = Set.copyOf(Objects.requireNonNull(resourceReads, "resourceReads"));
    resourceWrites = Set.copyOf(Objects.requireNonNull(resourceWrites, "resourceWrites"));
  }

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param call the call input; null handling follows the validation performed by this declaration
   * @param mode the mode input; null handling follows the validation performed by this declaration
   */
  AgentScheduledToolCall(LlmToolCall call, AgentToolExecutionMode mode) {
    this(call, mode, Set.of(), Set.of());
  }

  /**
   * Reports whether this scheduled call is a parallel-safe read.
   *
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  boolean isParallelRead() {
    return mode == AgentToolExecutionMode.PARALLEL_READ;
  }

  /**
   * Reports whether resource metadata is available for the call.
   *
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  boolean hasKnownResources() {
    return !resourceReads.isEmpty() || !resourceWrites.isEmpty();
  }
}
