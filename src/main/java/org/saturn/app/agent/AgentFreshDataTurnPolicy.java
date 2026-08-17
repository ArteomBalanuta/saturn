package org.saturn.app.agent;

/** Blocks response policies until the required fresh-data tool has succeeded. */
final class AgentFreshDataTurnPolicy implements AgentTurnPolicy {
  @Override
  public AgentTurnPolicyResult apply(AgentTurnPolicyInput input) {
    if (input.requiredFreshTool().isPresent()
        && !input.turnState().hasSuccessfulTool(input.requiredFreshTool().orElseThrow())) {
      return AgentTurnPolicyResult.stop(input.response());
    }
    return new AgentTurnPolicyResult(input.response(), false);
  }
}
