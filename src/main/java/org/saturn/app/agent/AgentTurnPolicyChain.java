package org.saturn.app.agent;

import java.util.List;
import java.util.Objects;

/** Applies turn policies in deterministic order while carrying the current response forward. */
final class AgentTurnPolicyChain implements AgentTurnPolicy {
  private final List<AgentTurnPolicy> policies;

  AgentTurnPolicyChain(List<AgentTurnPolicy> policies) {
    this.policies = policies.stream().map(Objects::requireNonNull).toList();
  }

  @Override
  public AgentTurnPolicyResult apply(AgentTurnPolicyInput input)
      throws AgentRoutingException, org.saturn.app.agent.llm.LlmException {
    AgentTurnPolicyInput current = input;
    boolean correctionUsed = false;
    for (AgentTurnPolicy policy : policies) {
      AgentTurnPolicyResult result = policy.apply(current);
      correctionUsed |= result.correctionUsed();
      current =
          new AgentTurnPolicyInput(
              result.response(),
              current.messages(),
              current.definitions(),
              current.commandProseGuard(),
              current.turnState(),
              current.prompt(),
              current.correlationId());
    }
    return new AgentTurnPolicyResult(current.response(), correctionUsed);
  }
}
