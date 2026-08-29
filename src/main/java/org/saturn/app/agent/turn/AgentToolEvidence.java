package org.saturn.app.agent.turn;

/**
 * Request-local evidence of provider tool-call attempts and outcomes.
 *
 * @param attempted whether at least one tool call was attempted
 * @param attemptedCount number of attempted calls
 * @param successfulCount number of successful calls
 * @param failedCount number of failed calls
 */
public record AgentToolEvidence(
    boolean attempted, int attemptedCount, int successfulCount, int failedCount) {
  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param attempted the attempted input; null handling follows the validation performed by this
   *     declaration
   * @param attemptedCount the attemptedCount input; null handling follows the validation performed
   *     by this declaration
   * @param successfulCount the successfulCount input; null handling follows the validation
   *     performed by this declaration
   * @param failedCount the failedCount input; null handling follows the validation performed by
   *     this declaration
   */
  public AgentToolEvidence {
    if (attemptedCount < 0
        || successfulCount < 0
        || failedCount < 0
        || successfulCount + failedCount != attemptedCount
        || attempted != (attemptedCount > 0)) {
      throw new IllegalArgumentException("inconsistent tool evidence");
    }
  }

  /**
   * Implements the {@code none} operation for this agent component.
   *
   * @return the operation result
   */
  public static AgentToolEvidence none() {
    return new AgentToolEvidence(false, 0, 0, 0);
  }
}
