package org.saturn.app.agent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.saturn.app.agent.llm.LlmResponse;

/** Validates the final response contract after required fresh data has been collected. */
final class AgentFreshDataFinalValidator {
  private final AgentFreshDataPolicy policy;

  AgentFreshDataFinalValidator(AgentFreshDataPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  void validate(
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
