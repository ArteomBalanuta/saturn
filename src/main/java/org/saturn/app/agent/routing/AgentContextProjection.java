package org.saturn.app.agent.routing;

import java.util.List;
import org.saturn.app.agent.llm.LlmMessage;

/** Immutable accounting for one provider-facing context projection. */
public record AgentContextProjection(
    List<LlmMessage> messages,
    int serializedChars,
    int estimatedTokens,
    int budgetChars,
    boolean pruned,
    boolean overflow,
    int removedUnits,
    String fingerprint) {
  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param messages the messages input; null handling follows the validation performed by this
   *     declaration
   * @param serializedChars the serializedChars input; null handling follows the validation
   *     performed by this declaration
   * @param estimatedTokens the estimatedTokens input; null handling follows the validation
   *     performed by this declaration
   * @param budgetChars the budgetChars input; null handling follows the validation performed by
   *     this declaration
   * @param pruned the pruned input; null handling follows the validation performed by this
   *     declaration
   * @param overflow the overflow input; null handling follows the validation performed by this
   *     declaration
   * @param removedUnits the removedUnits input; null handling follows the validation performed by
   *     this declaration
   * @param fingerprint the fingerprint input; null handling follows the validation performed by
   *     this declaration
   */
  public AgentContextProjection {
    messages = List.copyOf(messages);
    fingerprint = fingerprint == null ? "" : fingerprint;
  }
}
