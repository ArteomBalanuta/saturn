package org.saturn.app.agent.moderation;

@FunctionalInterface
public interface ModerationActionExecutor {
  boolean execute(ModerationDecision decision);

  static ModerationActionExecutor none() {
    return ignored -> true;
  }
}
