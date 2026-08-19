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

  public AgentFreshDataFinalValidator(AgentFreshDataPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

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
