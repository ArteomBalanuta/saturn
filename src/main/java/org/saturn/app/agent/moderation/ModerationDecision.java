package org.saturn.app.agent.moderation;

import java.util.Objects;
import java.util.Optional;

public record ModerationDecision(ModerationAction action, Optional<String> target, String reason) {
  public ModerationDecision {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(reason, "reason");
    target = target.map(String::strip).filter(value -> !value.isEmpty());
    if (action != ModerationAction.CAPTCHA_ON && target.isEmpty()) {
      throw new IllegalArgumentException(action + " requires a target");
    }
    if (action == ModerationAction.CAPTCHA_ON && target.isPresent()) {
      throw new IllegalArgumentException("CAPTCHA_ON is a room action");
    }
  }

  public static ModerationDecision targeted(ModerationAction action, String target, String reason) {
    return new ModerationDecision(action, Optional.ofNullable(target), reason);
  }

  public static ModerationDecision room(ModerationAction action, String reason) {
    return new ModerationDecision(action, Optional.empty(), reason);
  }
}
