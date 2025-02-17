package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getWhiteListedTrips;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.OutService;

@Slf4j
@CommandAliases(aliases = {"replicaoff", "offline", "botoff", "agentoff"})
public class ReplicaOffCommandImpl extends UserCommandBaseImpl {
  private final OutService outService;

  public ReplicaOffCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getWhiteListedTrips(engine));
    super.setAliases(aliases);
    this.outService = super.engine.outService;
  }

  @Override
  public Optional<Status> execute() {
    String author = super.chatMessage.getNick();

    List<String> arguments = this.getArguments();
    if (arguments.isEmpty()) {
      outService.enqueueMessageForSending(
          author, "Example: " + engine.prefix + "replicaoff lounge", isWhisper());
      log.info("Executed [replicaoff] command by user: {}, no channel set", author);
      return Optional.of(Status.FAILED);
    }

    String channel = arguments.getFirst().trim();
    if (channel.isBlank() || channel.equals(engine.channel)) {
      outService.enqueueMessageForSending(
          author, "I'm the host bot serving current channel, not a replica.", isWhisper());
    } else {
      EngineImpl replica = engine.replicasMappedByChannel.get(channel);
      if (replica == null) {
        log.warn("No replica in channel: {}", channel);
        outService.enqueueMessageForSending(
            author, "No replica in channel: " + channel, isWhisper());
      } else {
        replica.stop();
        engine.replicasMappedByChannel.remove(channel);
        log.info("Successfully shut down replica in channel: {}", channel);
        outService.enqueueMessageForSending(
            author, "Successfully shut down replica in channel: " + channel, isWhisper());
      }
    }

    log.info("Executed [replicaoff] command by user: {}, channel: {}", author, channel);
    return Optional.of(Status.SUCCESSFUL);
  }
}
