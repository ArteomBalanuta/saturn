package org.saturn.app.agent.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.User;

/** Engine-backed implementation of the live room directory used by {@link RoomUsersTool}. */
public final class EngineAgentRoomDirectory implements AgentRoomDirectory {
  private final EngineImpl engine;

  /**
   * Implements the {@code EngineAgentRoomDirectory} operation for this agent component.
   *
   * @param engine input argument used by this operation
   */
  public EngineAgentRoomDirectory(EngineImpl engine) {
    this.engine = Objects.requireNonNull(engine, "engine");
  }

  /**
   * Implements the {@code find} operation for this agent component.
   *
   * @param room input argument used by this operation
   * @return the operation result
   */
  @Override
  public Optional<RoomSnapshot> find(String room) {
    if (room == null || room.isBlank()) {
      return Optional.empty();
    }
    String requestedRoom = room.trim();
    return managedEngines().stream()
        .filter(candidate -> requestedRoom.equalsIgnoreCase(candidate.channel))
        .findFirst()
        .map(this::snapshot);
  }

  /**
   * Implements the {@code managedEngines} operation for this agent component.
   *
   * @return the operation result
   */
  private List<EngineImpl> managedEngines() {
    EngineImpl root =
        engine.engineType == EngineType.HOST || engine.getHostRef() == null
            ? engine
            : engine.getHostRef();
    List<EngineImpl> engines = new ArrayList<>();
    engines.add(root);
    engines.addAll(root.replicasMappedByChannel.values());
    if (!engines.contains(engine)) {
      engines.add(engine);
    }
    return List.copyOf(engines);
  }

  /**
   * Implements the {@code snapshot} operation for this agent component.
   *
   * @param source input argument used by this operation
   * @return the operation result
   */
  private RoomSnapshot snapshot(EngineImpl source) {
    List<String> users =
        source.currentChannelUsers.stream().map(User::getNick).filter(Objects::nonNull).toList();
    return new RoomSnapshot(source.channel, users);
  }
}
