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

  /**
   * Implements the {@code from} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @return the operation result
   */
  public static AgentUserIdentity from(AgentContext context) {
    Objects.requireNonNull(context, "context");
    return from(context.trip(), context.hash(), context.nick());
  }

  /**
   * Implements the {@code from} operation for this agent component.
   *
   * @param message input argument used by this operation
   * @return the operation result
   */
  public static AgentUserIdentity from(ChatMessage message) {
    Objects.requireNonNull(message, "message");
    return from(message.getTrip(), message.getHash(), message.getNick());
  }

  /**
   * Implements the {@code from} operation for this agent component.
   *
   * @param user input argument used by this operation
   * @return the operation result
   */
  public static AgentUserIdentity from(User user) {
    Objects.requireNonNull(user, "user");
    return from(user.getTrip(), user.getHash(), user.getNick());
  }

  /**
   * Implements the {@code from} operation for this agent component.
   *
   * @param trip input argument used by this operation
   * @param hash input argument used by this operation
   * @param nick input argument used by this operation
   * @return the operation result
   */
  private static AgentUserIdentity from(String trip, String hash, String nick) {
    if (trip != null && !trip.isBlank()) {
      return new AgentUserIdentity("trip:" + normalize(trip));
    }
    if (hash != null && !hash.isBlank()) {
      return new AgentUserIdentity("hash:" + normalize(hash));
    }
    return new AgentUserIdentity("nick:" + normalize(Objects.requireNonNull(nick, "nick")));
  }

  /**
   * Implements the {@code normalize} operation for this agent component.
   *
   * @param value input argument used by this operation
   * @return the operation result
   */
  private static String normalize(String value) {
    return value.strip().toLowerCase(Locale.ROOT);
  }
}
