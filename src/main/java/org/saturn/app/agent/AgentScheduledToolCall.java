package org.saturn.app.agent;

import java.util.Objects;
import org.saturn.app.agent.llm.LlmToolCall;

/** A provider tool call paired with its request-local execution classification. */
record AgentScheduledToolCall(LlmToolCall call, AgentToolExecutionMode mode) {
  AgentScheduledToolCall {
    call = Objects.requireNonNull(call, "call");
    mode = Objects.requireNonNull(mode, "mode");
  }

  boolean isParallelRead() {
    return mode == AgentToolExecutionMode.PARALLEL_READ;
  }
}
