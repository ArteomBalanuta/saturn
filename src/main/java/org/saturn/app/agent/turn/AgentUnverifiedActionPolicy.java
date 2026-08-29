package org.saturn.app.agent.turn;

import org.saturn.app.agent.api.AgentRoutingException;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.routing.AgentResponseCorrector;

/** Applies the bounded correction for model responses that narrate unverified actions. */
public final class AgentUnverifiedActionPolicy implements AgentTurnPolicy {
  private final AgentResponseCorrector responseCorrector;

  /**
   * Implements the {@code AgentUnverifiedActionPolicy} operation for this agent component.
   *
   * @param responseCorrector input argument used by this operation
   */
  public AgentUnverifiedActionPolicy(AgentResponseCorrector responseCorrector) {
    this.responseCorrector = responseCorrector;
  }

  /**
   * Implements the {@code apply} operation for this agent component.
   *
   * @param input input argument used by this operation
   * @return the operation result
   */
  @Override
  public AgentTurnPolicyResult apply(AgentTurnPolicyInput input)
      throws LlmException, AgentRoutingException {
    LlmResponse response = input.response();
    if (input.turnState().unverifiedActionChecked()) {
      return new AgentTurnPolicyResult(response, false);
    }
    if (!input.turnState().hasSuccessfulCommands()
        || input.commandProseGuard().findCommand(response.content()).isEmpty()) {
      response =
          responseCorrector.correctUnverifiedActionClaim(
              response, input.messages(), input.definitions(), input.correlationId());
      input.turnState().markUnverifiedActionChecked();
    }
    return new AgentTurnPolicyResult(response, false);
  }
}
