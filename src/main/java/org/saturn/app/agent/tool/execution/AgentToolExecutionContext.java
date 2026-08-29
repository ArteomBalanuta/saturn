package org.saturn.app.agent.tool.execution;

import java.time.Instant;
import java.util.Set;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.llm.LlmToolCall;

/** Immutable context exposed to execution policies. */
public record AgentToolExecutionContext(
    AgentContext agentContext,
    LlmToolCall call,
    AgentToolDescriptor descriptor,
    String invocationKey,
    Set<String> allowedTools,
    Instant deadline,
    CancellationToken cancellation,
    boolean approvalPresent) {
  public AgentToolExecutionContext {
    allowedTools = Set.copyOf(allowedTools);
  }
}
