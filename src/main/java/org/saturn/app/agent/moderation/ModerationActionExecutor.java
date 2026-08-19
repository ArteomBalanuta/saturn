package org.saturn.app.agent.moderation;

@FunctionalInterface
/** Executes a moderation action selected by the room moderation policy. */
public interface ModerationActionExecutor {
  boolean execute(ModerationDecision decision);

  static ModerationActionExecutor none() {
    return ignored -> true;
  }
}
