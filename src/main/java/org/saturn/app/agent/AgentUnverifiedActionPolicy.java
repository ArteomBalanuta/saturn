package org.saturn.app.agent;

import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmResponse;

/** Applies the bounded correction for model responses that narrate unverified actions. */
final class AgentUnverifiedActionPolicy implements AgentTurnPolicy {
  private final AgentResponseCorrector responseCorrector;

  AgentUnverifiedActionPolicy(AgentResponseCorrector responseCorrector) {
    this.responseCorrector = responseCorrector;
  }

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
