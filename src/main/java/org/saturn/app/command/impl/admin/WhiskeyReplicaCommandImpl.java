package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import com.moandjiezana.toml.Toml;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.OutService;

@Slf4j
@CommandAliases(aliases = {"whiskey"})
public class WhiskeyReplicaCommandImpl extends UserCommandBaseImpl {
  private final OutService outService;

  public WhiskeyReplicaCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
    this.outService = super.engine.outService;
  }

  @Override
  public Optional<Status> execute() {
    String author = super.chatMessage.getNick();

    List<String> arguments = this.getArguments();

    String channel = arguments.getFirst().trim();
    if (engine.replicasMappedByChannel.get(channel) == null) {
      log.debug("Registering agent for channel: {}", channel);
      registerReplica(engine, chatMessage, author, channel);
      log.info("Successfully started agent for channel: {}", channel);
    }

    log.info("Executed [whiskey] command by user: {}, channel: {}", author, channel);
    return Optional.of(Status.SUCCESSFUL);
  }

  public static void registerReplica(
      EngineImpl engine, ChatMessage chatMessage, String author, String channel) {
    Toml main = engine.getConfig();
    EngineImpl replica = new EngineImpl(engine.getDbConnection(), main, EngineType.AGENT);
    replica.setChannel(channel);
    replica.setNick("portal");
    replica.setPassword("portal");

    /* register replica */
    engine.addReplica(replica);

    replica.start();

    engine.outService.enqueueMessageForSending(
        author,
        "started replica at whiskey channel: "
            + channel
            + " successfully. Number of agents: "
            + engine.replicasMappedByChannel.size(),
        chatMessage.isWhisper());
  }
}
