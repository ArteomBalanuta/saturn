package org.saturn.app.agent.turn;

import java.util.Objects;
import org.saturn.app.agent.llm.LlmResponse;

/** Explicit outcome from one ordered turn policy. */
public record AgentTurnPolicyResult(
    LlmResponse response, boolean correctionUsed, boolean continuePolicyEvaluation) {
  public AgentTurnPolicyResult(LlmResponse response, boolean correctionUsed) {
    this(response, correctionUsed, true);
  }

  public AgentTurnPolicyResult {
    Objects.requireNonNull(response, "response");
  }

  static AgentTurnPolicyResult stop(LlmResponse response) {
    return new AgentTurnPolicyResult(response, false, false);
  }
}
