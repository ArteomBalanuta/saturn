package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getWhiteListedTrips;

import com.moandjiezana.toml.Toml;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.OutService;

@Slf4j
@CommandAliases(aliases = {"replica", "bot", "agent"})
public class ReplicaCommandImpl extends UserCommandBaseImpl {
  private final OutService outService;

  public ReplicaCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
          author, "Example: " + engine.prefix + "replica lounge", isWhisper());
      return Optional.of(Status.FAILED);
    }

    String channel = arguments.getFirst().trim();
    if (channel.isBlank() || channel.equals(engine.channel)) {
      outService.enqueueMessageForSending(
          author,
          "I'm the host bot serving current channel. Example: " + engine.prefix + "replica lounge",
          isWhisper());
    } else {
      if (engine.replicasMappedByChannel.get(channel) == null) {
        log.debug("Registering replica for channel: {}", channel);
        registerReplica(engine, chatMessage, author, channel);
        log.info("Successfully started replica for channel: {}", channel);
      } else {
        log.warn("Channel: {} already has a replica running", channel);
      }
    }

    log.info("Executed [replica] command by user: {}, channel: {}", author, channel);
    return Optional.of(Status.SUCCESSFUL);
  }

  public static void registerReplica(
      EngineImpl engine, ChatMessage chatMessage, String author, String channel) {
    Toml main = engine.getConfig();
    EngineImpl replica = new EngineImpl(engine.getDbConnection(), main, EngineType.REPLICA);
    replica.setChannel(channel);
    replica.setNick(engine.nick.concat("Replica"));
    replica.setPassword(engine.password);

    /* register replica */
    engine.addReplica(replica);

    replica.start();

    engine.outService.enqueueMessageForSending(
        author,
        "started replica in channel: "
            + channel
            + " successfully. Number of agents: "
            + engine.replicasMappedByChannel.size(),
        chatMessage.isWhisper());
  }
}
