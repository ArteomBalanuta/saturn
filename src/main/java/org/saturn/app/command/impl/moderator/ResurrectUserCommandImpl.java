package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

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

@Slf4j
@CommandAliases(aliases = {"move", "recover", "heal", "resurrect"})
public class ResurrectUserCommandImpl extends UserCommandBaseImpl {
  private final List<String> aliases = new ArrayList<>();

  public ResurrectUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(this.getAliases());
    this.aliases.addAll(aliases);
  }

  @Override
  public List<String> getAliases() {
    return this.aliases;
  }

  @Override
  public List<String> getArguments() {
    return super.getArguments();
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    List<String> arguments = getArguments();
    String author = chatMessage.getNick();

    if (arguments.isEmpty() && resurrectLastKicked(this.engine.channel)) {
      EngineImpl slaveEngine = new EngineImpl(null, super.engine.getConfig(), EngineType.LIST_CMD);
      resurrect(kickedTo, lastKicked, this.engine.channel, slaveEngine);
      log.info("Executed [move] command by user: {} - resurrected last moved user", author);
      return Optional.of(Status.SUCCESSFUL);
    }

    if (arguments.size() != 3) {
      super.engine.outService.enqueueMessageForSending(
          author, " " + engine.prefix + "move <nick> <from> <to>", isWhisper());
      log.info("Executed [move] command by user: {} - missing required parameters", author);
      return Optional.of(Status.FAILED);
    }

    String from = arguments.get(1);
    String target = arguments.get(0).replace("@", "");
    String to = arguments.get(2);

    log.info("Moving user: {}, from: {}, to: {}", target, from, to);

    if (super.engine.replicasMappedByChannel.containsKey(from)
        || super.engine.getHostRef().channel.equals(from)) {
      /* got an instance in the room already or host room */
      EngineImpl replica = null;
      boolean isReplicaPresent = super.engine.replicasMappedByChannel.containsKey(from);
      if (!isReplicaPresent) {
        /* using host */
        replica = super.engine.getHostRef();
      } else {
        replica = super.engine.replicasMappedByChannel.get(from);
      }

      replica.outService.enqueueRawMessageForSending(
          String.format("{ \"cmd\": \"kick\", \"nick\": \"%s\", \"to\":\"%s\"}", target, to));
      replica.shareMessages();
    } else {
      Toml main = super.engine.getConfig();
      EngineImpl slaveEngine = new EngineImpl(null, main, EngineType.LIST_CMD);
      resurrect(from, target, to, slaveEngine);
    }

    log.info("Executed [move] command by user: {}, target: {}", author, target);
    return Optional.of(Status.SUCCESSFUL);
  }
}
