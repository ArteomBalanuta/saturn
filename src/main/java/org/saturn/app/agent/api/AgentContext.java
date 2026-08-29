package org.saturn.app.agent.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable caller, room, capability, and privacy context passed to every agent tool. */
public record AgentContext(
    String room,
    String nick,
    String trip,
    String hash,
    boolean whisper,
    List<String> roomUsers,
    Set<AgentCapability> capabilities,
    String moderationTarget) {
  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param room the room input; null handling follows the validation performed by this declaration
   * @param nick the nick input; null handling follows the validation performed by this declaration
   * @param trip the trip input; null handling follows the validation performed by this declaration
   * @param hash the hash input; null handling follows the validation performed by this declaration
   * @param whisper the whisper input; null handling follows the validation performed by this
   *     declaration
   * @param roomUsers the roomUsers input; null handling follows the validation performed by this
   *     declaration
   * @param capabilities the capabilities input; null handling follows the validation performed by
   *     this declaration
   * @param moderationTarget the moderationTarget input; null handling follows the validation
   *     performed by this declaration
   */
  public AgentContext {
    Objects.requireNonNull(room, "room");
    Objects.requireNonNull(nick, "nick");
    Objects.requireNonNull(roomUsers, "roomUsers");
    Objects.requireNonNull(capabilities, "capabilities");
    roomUsers = List.copyOf(roomUsers);
    capabilities = Set.copyOf(capabilities);
  }

  /**
   * Implements the {@code AgentContext} operation for this agent component.
   *
   * @param room input argument used by this operation
   * @param nick input argument used by this operation
   * @param trip input argument used by this operation
   * @param hash input argument used by this operation
   * @param whisper input argument used by this operation
   * @param roomUsers input argument used by this operation
   */
  public AgentContext(
      String room, String nick, String trip, String hash, boolean whisper, List<String> roomUsers) {
    this(room, nick, trip, hash, whisper, roomUsers, Set.of(), null);
  }

  /**
   * Implements the {@code AgentContext} operation for this agent component.
   *
   * @param room input argument used by this operation
   * @param nick input argument used by this operation
   * @param trip input argument used by this operation
   * @param hash input argument used by this operation
   * @param whisper input argument used by this operation
   * @param roomUsers input argument used by this operation
   * @param capabilities input argument used by this operation
   */
  public AgentContext(
      String room,
      String nick,
      String trip,
      String hash,
      boolean whisper,
      List<String> roomUsers,
      Set<AgentCapability> capabilities) {
    this(room, nick, trip, hash, whisper, roomUsers, capabilities, null);
  }

  /**
   * Implements the {@code hasCapability} operation for this agent component.
   *
   * @param capability input argument used by this operation
   * @return the operation result
   */
  public boolean hasCapability(AgentCapability capability) {
    return capabilities.contains(capability);
  }

  /**
   * Implements the {@code memoryKey} operation for this agent component.
   *
   * @return the operation result
   */
  public String memoryKey() {
    String roomKey = "%d:%s".formatted(room.length(), room);
    if (!whisper) {
      return roomKey + "|public";
    }
    String identity;
    if (trip != null && !trip.isBlank()) {
      identity = "trip:" + trip;
    } else if (hash != null && !hash.isBlank()) {
      identity = "hash:" + hash;
    } else {
      identity = "nick:" + nick;
    }
    return roomKey + "|whisper|" + identity;
  }
}
