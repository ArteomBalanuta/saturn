package org.saturn.app.model;

import java.util.Objects;

public record MessageAuditEvent(
    String trip,
    String nick,
    String hash,
    String message,
    String channel,
    long createdOn,
    Visibility visibility) {

  public MessageAuditEvent {
    Objects.requireNonNull(visibility, "visibility");
  }

  public static MessageAuditEvent publicMessage(
      String trip, String nick, String hash, String message, String channel, long createdOn) {
    return new MessageAuditEvent(trip, nick, hash, message, channel, createdOn, Visibility.PUBLIC);
  }

  public static MessageAuditEvent whisper(
      String trip, String nick, String hash, String message, String channel, long createdOn) {
    return new MessageAuditEvent(trip, nick, hash, message, channel, createdOn, Visibility.WHISPER);
  }

  public enum Visibility {
    PUBLIC,
    WHISPER
  }
}
