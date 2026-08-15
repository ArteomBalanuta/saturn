package org.saturn.app.agent;

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
  public AgentContext {
    Objects.requireNonNull(room, "room");
    Objects.requireNonNull(nick, "nick");
    Objects.requireNonNull(roomUsers, "roomUsers");
    Objects.requireNonNull(capabilities, "capabilities");
    roomUsers = List.copyOf(roomUsers);
    capabilities = Set.copyOf(capabilities);
  }

  public AgentContext(
      String room, String nick, String trip, String hash, boolean whisper, List<String> roomUsers) {
    this(room, nick, trip, hash, whisper, roomUsers, Set.of(), null);
  }

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

  public boolean hasCapability(AgentCapability capability) {
    return capabilities.contains(capability);
  }

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
