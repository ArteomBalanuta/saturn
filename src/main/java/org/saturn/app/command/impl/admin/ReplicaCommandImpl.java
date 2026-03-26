package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import com.moandjiezana.toml.Toml;
import org.saturn.app.service.impl.DataBaseServiceImpl;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"replica", "bot", "agent"})
public class ReplicaCommandImpl extends UserCommandBaseImpl {
  public ReplicaCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Optional<Status> execute() {
    List<String> arguments = getArguments();
    if (arguments.isEmpty()) {
      return failWithUsage("replica lounge");
    }

    String channel = arguments.getFirst().trim();
    if (channel.isBlank() || channel.equals(engine.channel)) {
      replyToAuthor(
          "I'm the host bot serving current channel. Example: %sreplica lounge"
              .formatted(engine.prefix));
      return Optional.of(Status.FAILED);
    }

    if (engine.replicasMappedByChannel.containsKey(channel)) {
      replyToAuthor("Channel %s already has a replica running.".formatted(channel));
      log.warn("Channel: {} already has a replica running", channel);
      return Optional.of(Status.FAILED);
    }

    log.debug("Registering replica for channel: {}", channel);
    registerReplica(engine, chatMessage, author(), channel);
    log.info("Successfully started replica for channel: {}", channel);
    log.info("Executed [replica] command by user: {}, channel: {}", author(), channel);
    return successful();
  }

  public static void registerReplica(
      EngineImpl engine, ChatMessage chatMessage, String author, String channel) {
    Toml main = engine.getConfig();
    EngineImpl replica =
        new EngineImpl(new DataBaseServiceImpl(engine.dbPath).getConnection(), main, EngineType.REPLICA);
    replica.setChannel(channel);
    replica.setNick(engine.nick.concat("Replica"));
    replica.setPassword(engine.password);

    /* register replica */
    engine.addReplica(replica);

    replica.start();

    engine.outService.enqueueMessageForSending(
        author,
        "started replica in channel: %s successfully. Number of replicas: %s"
            .formatted(channel, engine.replicasMappedByChannel.size()),
        chatMessage.isWhisper());
  }
}
