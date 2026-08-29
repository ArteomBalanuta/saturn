package org.saturn.app.agent.turn;

/** Blocks response policies until the required fresh-data tool has succeeded. */
public final class AgentFreshDataTurnPolicy implements AgentTurnPolicy {
  /**
   * Implements the {@code apply} operation for this agent component.
   *
   * @param input input argument used by this operation
   * @return the operation result
   */
  @Override
  public AgentTurnPolicyResult apply(AgentTurnPolicyInput input) {
    if (input.requiredFreshTool().isPresent()
        && !input.turnState().hasSuccessfulTool(input.requiredFreshTool().orElseThrow())) {
      return AgentTurnPolicyResult.stop(input.response());
    }
    return new AgentTurnPolicyResult(input.response(), false);
  }
}
