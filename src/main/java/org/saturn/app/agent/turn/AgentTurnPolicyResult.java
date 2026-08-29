package org.saturn.app.agent.turn;

import java.util.Objects;
import org.saturn.app.agent.llm.LlmResponse;

/**
 * Explicit outcome from one ordered turn policy.
 *
 * @param response response to pass to subsequent policy processing
 * @param correctionUsed whether this policy consumed its correction allowance
 * @param continuePolicyEvaluation whether later policies should run
 */
public record AgentTurnPolicyResult(
    LlmResponse response, boolean correctionUsed, boolean continuePolicyEvaluation) {
  /**
   * Implements the {@code AgentTurnPolicyResult} operation for this agent component.
   *
   * @param response input argument used by this operation
   * @param correctionUsed input argument used by this operation
   */
  public AgentTurnPolicyResult(LlmResponse response, boolean correctionUsed) {
    this(response, correctionUsed, true);
  }

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param response the response input; null handling follows the validation performed by this
   *     declaration
   * @param correctionUsed the correctionUsed input; null handling follows the validation performed
   *     by this declaration
   * @param continuePolicyEvaluation the continuePolicyEvaluation input; null handling follows the
   *     validation performed by this declaration
   */
  public AgentTurnPolicyResult {
    Objects.requireNonNull(response, "response");
  }

  /**
   * Implements the {@code stop} operation for this agent component.
   *
   * @param response input argument used by this operation
   * @return the operation result
   */
  static AgentTurnPolicyResult stop(LlmResponse response) {
    return new AgentTurnPolicyResult(response, false, false);
  }
}
