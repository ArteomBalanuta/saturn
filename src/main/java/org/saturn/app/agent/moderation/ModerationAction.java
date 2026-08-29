package org.saturn.app.agent.moderation;

/** Defines the moderation actions that may be applied to a room participant. */
public enum ModerationAction {
  WARN,
  CAPTCHA_ON,
  MUTE,
  KICK,
  SHADOWBAN
}
