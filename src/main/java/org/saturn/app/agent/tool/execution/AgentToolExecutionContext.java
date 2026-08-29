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
  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param agentContext the agentContext input; null handling follows the validation performed by
   *     this declaration
   * @param call the call input; null handling follows the validation performed by this declaration
   * @param descriptor the descriptor input; null handling follows the validation performed by this
   *     declaration
   * @param invocationKey the invocationKey input; null handling follows the validation performed by
   *     this declaration
   * @param allowedTools the allowedTools input; null handling follows the validation performed by
   *     this declaration
   * @param deadline the deadline input; null handling follows the validation performed by this
   *     declaration
   * @param cancellation the cancellation input; null handling follows the validation performed by
   *     this declaration
   * @param approvalPresent the approvalPresent input; null handling follows the validation
   *     performed by this declaration
   */
  public AgentToolExecutionContext {
    allowedTools = Set.copyOf(allowedTools);
  }
}
