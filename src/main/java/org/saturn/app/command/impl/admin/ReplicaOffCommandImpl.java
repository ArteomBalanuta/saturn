package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"replicaoff", "offline", "botoff", "agentoff"})
public class ReplicaOffCommandImpl extends UserCommandBaseImpl {
  public ReplicaOffCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Optional<Status> execute() {
    List<String> arguments = getArguments();
    if (arguments.isEmpty()) {
      replyToAuthor("Example: %sreplicaoff lounge".formatted(engine.prefix));
      log.info("Executed [replicaoff] command by user: {}, no channel set", author());
      return Optional.of(Status.FAILED);
    }

    String channel = arguments.getFirst().trim();
    if (channel.isBlank() || channel.equals(engine.channel)) {
      replyToAuthor("I'm the host bot serving current channel, not a replica.");
      return Optional.of(Status.FAILED);
    }

    EngineImpl replica = engine.replicasMappedByChannel.get(channel);
    if (replica == null) {
      log.warn("No replica in channel: {}", channel);
      replyToAuthor("No replica in channel: %s".formatted(channel));
      return Optional.of(Status.FAILED);
    }

    replica.stop();
    engine.replicasMappedByChannel.remove(channel);
    log.info("Successfully shut down replica in channel: {}", channel);
    replyToAuthor("Successfully shut down replica in channel: %s".formatted(channel));
    log.info("Executed [replicaoff] command by user: {}, channel: {}", author(), channel);
    return successful();
  }
}
