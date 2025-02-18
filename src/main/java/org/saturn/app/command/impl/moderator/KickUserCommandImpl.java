package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"kick", "k", "out"})
public class KickUserCommandImpl extends UserCommandBaseImpl {
  public KickUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    final List<String> arguments =
        getArguments().stream().map(arg -> arg.replace("@", "")).toList();

    final String author = chatMessage.getNick();
    if (arguments.isEmpty() && resurrectLastKicked(this.engine.channel)) {
      EngineImpl slaveEngine = new EngineImpl(null, super.engine.getConfig(), EngineType.LIST_CMD);
      resurrect(kickedTo, lastKicked, this.engine.channel, slaveEngine);
      log.info("Executed [kick] command by user: {} - resurrected last kicked user", author);
      return Optional.of(Status.SUCCESSFUL);
    }

    if (arguments.isEmpty() && (lastKicked == null && kickedTo == null)) {
      log.info("Executed [kick] command by user: {}, no username parameter specified", author);
      engine.outService.enqueueMessageForSending(
          author, "\\n Example: " + engine.prefix + "kick merc", isWhisper());
      return Optional.of(Status.FAILED);
    }

    String flag = arguments.getFirst();

    List<String> activeUsers =
        engine.currentChannelUsers.stream().map(User::getNick).collect(Collectors.toList());

    switch (flag) {
      case "-m" -> {
        List<String> usernames = arguments.stream().skip(1).toList();
        for (String target : usernames) {
          kickUserIfPresent(target, activeUsers);
        }
      }
      case "-c" -> {
        String value = arguments.get(1);
        List<String> usernames =
            activeUsers.stream()
                .filter(username -> username.contains(value))
                .collect(Collectors.toList());

        log.info("Kicking users: {}", usernames);

        for (String target : usernames) {
          engine.modService.kick(target);
          log.info("Kicked: {}", target);
        }
      }
      default -> kickUserIfPresent(flag, activeUsers);
    }

    log.info("Executed kick command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }

  private void kickUserIfPresent(String target, List<String> activeUsers) {
    if (activeUsers.contains(target)) {
      engine.modService.kick(target);
      lastKicked = target;
      log.info("Kicked: {}", target);
    } else {
      log.info("User: {} is not in the room", target);
    }
  }
}
