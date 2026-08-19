package org.saturn.app.agent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.saturn.app.agent.turn.AgentToolEvidence;

class AgentToolEvidenceTest {
  @Test
  void noneHasZeroCounts() {
    assertEquals(new AgentToolEvidence(false, 0, 0, 0), AgentToolEvidence.none());
  }

  @Test
  void countsMustPartitionAttempts() {
    assertThrows(IllegalArgumentException.class, () -> new AgentToolEvidence(true, 2, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> new AgentToolEvidence(false, 1, 1, 0));
  }
}
