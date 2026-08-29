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

  AgentScheduledToolCall(LlmToolCall call, AgentToolExecutionMode mode) {
    this(call, mode, Set.of(), Set.of());
  }

  boolean isParallelRead() {
    return mode == AgentToolExecutionMode.PARALLEL_READ;
  }

  boolean hasKnownResources() {
    return !resourceReads.isEmpty() || !resourceWrites.isEmpty();
  }
}
