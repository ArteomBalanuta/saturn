package org.saturn.app.agent.turn;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.saturn.app.agent.api.AgentRoutingException;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.llm.LlmResponse;

/** Validates the final response contract after required fresh data has been collected. */
public final class AgentFreshDataFinalValidator {
  private final AgentFreshDataPolicy policy;

  /**
   * Implements the {@code AgentFreshDataFinalValidator} operation for this agent component.
   *
   * @param policy input argument used by this operation
   */
  public AgentFreshDataFinalValidator(AgentFreshDataPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  /**
   * Implements the {@code validate} operation for this agent component.
   *
   * @param requiredFreshTool input argument used by this operation
   * @param response input argument used by this operation
   * @param successfulToolResults input argument used by this operation
   */
  public void validate(
      Optional<String> requiredFreshTool,
      LlmResponse response,
      List<AgentToolResult> successfulToolResults)
      throws AgentRoutingException {
    if (policy.requiresFinalSynthesisValidation(
        requiredFreshTool, response, successfulToolResults)) {
      throw new AgentRoutingException("Agent did not produce a complete fresh history synthesis");
    }
  }
}
