package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

import com.moandjiezana.toml.Toml;
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
import org.saturn.app.util.JsonPayloads;

@Slf4j
@CommandAliases(aliases = {"move", "recover", "heal", "resurrect"})
public class ResurrectUserCommandImpl extends UserCommandBaseImpl {
  public ResurrectUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    List<String> arguments = getArguments();
    if (arguments.isEmpty()) {
      return resurrectLastMovedUser();
    }

    if (arguments.size() != 3) {
      replyToAuthor(" %smove <nick> <from> <to>".formatted(engine.prefix));
      log.info("Executed [move] command by user: {} - missing required parameters", author());
      return Optional.of(Status.FAILED);
    }

    String target = arguments.get(0).replace("@", "");
    String from = arguments.get(1);
    String to = arguments.get(2);
    moveUser(target, from, to);
    log.info("Executed [move] command by user: {}, target: {}", author(), target);
    return successful();
  }

  private Optional<Status> resurrectLastMovedUser() {
    if (!resurrectLastKicked(this.engine.channel)) {
      log.info("Executed [move] command by user: {} - no last moved user available", author());
      return Optional.of(Status.FAILED);
    }

    EngineImpl slaveEngine = new EngineImpl(null, super.engine.getConfig(), EngineType.LIST_CMD);
    resurrect(kickedTo, lastKicked, this.engine.channel, slaveEngine);
    log.info("Executed [move] command by user: {} - resurrected last moved user", author());
    return successful();
  }

  private void moveUser(String target, String from, String to) {
    log.info("Moving user: {}, from: {}, to: {}", target, from, to);
    EngineImpl activeEngine = findEngineServing(from);
    if (activeEngine != null) {
      kickUserToChannel(activeEngine, target, to);
      return;
    }

    Toml main = super.engine.getConfig();
    EngineImpl slaveEngine = new EngineImpl(null, main, EngineType.LIST_CMD);
    resurrect(from, target, to, slaveEngine);
  }

  private EngineImpl findEngineServing(String channel) {
    EngineImpl replica = super.engine.replicasMappedByChannel.get(channel);
    if (replica != null) {
      return replica;
    }

    EngineImpl hostRef = super.engine.getHostRef();
    if (hostRef != null && channel.equals(hostRef.channel)) {
      return hostRef;
    }

    return null;
  }

  private void kickUserToChannel(EngineImpl sourceEngine, String target, String destination) {
    sourceEngine.outService.enqueueRawMessageForSending(
        JsonPayloads.command("kick", "nick", target, "to", destination));
    sourceEngine.shareMessages();
  }
}
