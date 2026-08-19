package org.saturn.app.agent.turn;

/** Request-local evidence of provider tool-call attempts and outcomes. */
public record AgentToolEvidence(
    boolean attempted, int attemptedCount, int successfulCount, int failedCount) {
  public AgentToolEvidence {
    if (attemptedCount < 0
        || successfulCount < 0
        || failedCount < 0
        || successfulCount + failedCount != attemptedCount
        || attempted != (attemptedCount > 0)) {
      throw new IllegalArgumentException("inconsistent tool evidence");
    }
  }

  public static AgentToolEvidence none() {
    return new AgentToolEvidence(false, 0, 0, 0);
  }
}
