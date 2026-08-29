package org.saturn.app.agent.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModerationDecisionTest {
  @Test
  void normalizesTargetedDecisions() {
    ModerationDecision decision =
        ModerationDecision.targeted(ModerationAction.WARN, "  alice  ", "burst");

    assertEquals(Optional.of("alice"), decision.target());
  }

  @Test
  void rejectsBlankTargetsForTargetedActions() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ModerationDecision.targeted(ModerationAction.WARN, "  ", "burst"));

    assertEquals("WARN requires a target", exception.getMessage());
  }

  @Test
  void rejectsTargetsForRoomCaptchaAction() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ModerationDecision.targeted(ModerationAction.CAPTCHA_ON, "alice", "raid"));

    assertEquals("CAPTCHA_ON is a room action", exception.getMessage());
  }
}
