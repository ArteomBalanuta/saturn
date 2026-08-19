package org.saturn.app.agent.api;

import java.util.Locale;
import java.util.Objects;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

/** Represents the room identity of the user associated with an agent operation. */
public record AgentUserIdentity(String value) {
  public AgentUserIdentity {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }

  public static AgentUserIdentity from(AgentContext context) {
    Objects.requireNonNull(context, "context");
    return from(context.trip(), context.hash(), context.nick());
  }

  public static AgentUserIdentity from(ChatMessage message) {
    Objects.requireNonNull(message, "message");
    return from(message.getTrip(), message.getHash(), message.getNick());
  }

  public static AgentUserIdentity from(User user) {
    Objects.requireNonNull(user, "user");
    return from(user.getTrip(), user.getHash(), user.getNick());
  }

  private static AgentUserIdentity from(String trip, String hash, String nick) {
    if (trip != null && !trip.isBlank()) {
      return new AgentUserIdentity("trip:" + normalize(trip));
    }
    if (hash != null && !hash.isBlank()) {
      return new AgentUserIdentity("hash:" + normalize(hash));
    }
    return new AgentUserIdentity("nick:" + normalize(Objects.requireNonNull(nick, "nick")));
  }

  private static String normalize(String value) {
    return value.strip().toLowerCase(Locale.ROOT);
  }
}
