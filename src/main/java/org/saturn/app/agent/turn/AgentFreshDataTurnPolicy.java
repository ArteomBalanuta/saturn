package org.saturn.app.agent.turn;

/** Blocks response policies until the required fresh-data tool has succeeded. */
public final class AgentFreshDataTurnPolicy implements AgentTurnPolicy {
  @Override
  public AgentTurnPolicyResult apply(AgentTurnPolicyInput input) {
    if (input.requiredFreshTool().isPresent()
        && !input.turnState().hasSuccessfulTool(input.requiredFreshTool().orElseThrow())) {
      return AgentTurnPolicyResult.stop(input.response());
    }
    return new AgentTurnPolicyResult(input.response(), false);
  }
}
