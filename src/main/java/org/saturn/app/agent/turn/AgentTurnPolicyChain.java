package org.saturn.app.agent.turn;

import java.util.List;
import java.util.Objects;
import org.saturn.app.agent.api.AgentRoutingException;

/** Applies turn policies in deterministic order while carrying the current response forward. */
public final class AgentTurnPolicyChain implements AgentTurnPolicy {
  private final List<AgentTurnPolicy> policies;

  public AgentTurnPolicyChain(List<AgentTurnPolicy> policies) {
    this.policies = policies.stream().map(Objects::requireNonNull).toList();
  }

  @Override
  public AgentTurnPolicyResult apply(AgentTurnPolicyInput input)
      throws AgentRoutingException, org.saturn.app.agent.llm.LlmException {
    AgentTurnPolicyInput current = input;
    boolean correctionUsed = false;
    boolean continuePolicyEvaluation = true;
    for (AgentTurnPolicy policy : policies) {
      AgentTurnPolicyResult result = policy.apply(current);
      correctionUsed |= result.correctionUsed();
      continuePolicyEvaluation = result.continuePolicyEvaluation();
      current =
          new AgentTurnPolicyInput(
              result.response(),
              current.messages(),
              current.definitions(),
              current.commandProseGuard(),
              current.turnState(),
              current.prompt(),
              current.correlationId(),
              current.requiredFreshTool());
      if (!result.continuePolicyEvaluation()) {
        break;
      }
    }
    return new AgentTurnPolicyResult(current.response(), correctionUsed, continuePolicyEvaluation);
  }
}
