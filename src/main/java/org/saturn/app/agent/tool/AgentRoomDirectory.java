package org.saturn.app.agent.tool;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@FunctionalInterface
/** Read-only bridge from agent tools to Saturn's live managed-room snapshots. */
public interface AgentRoomDirectory {
  Optional<RoomSnapshot> find(String room);

  record RoomSnapshot(String room, List<String> users) {
    public RoomSnapshot {
      Objects.requireNonNull(room, "room");
      Objects.requireNonNull(users, "users");
      users = List.copyOf(users);
    }
  }
}
