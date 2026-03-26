package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

import java.util.List;
import java.util.Optional;
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
    final List<String> arguments = sanitizeArguments(getArguments());
    final String author = author();

    if (arguments.isEmpty()) {
      return handleEmptyKickRequest(author);
    }

    String mode = arguments.getFirst();
    List<String> activeUsers = getActiveUserNicks();
    executeKickMode(mode, arguments, activeUsers);

    log.info("Executed kick command by user: {}", author);
    return successful();
  }

  private Optional<Status> handleEmptyKickRequest(String author) {
    if (resurrectLastKicked(this.engine.channel)) {
      EngineImpl slaveEngine = new EngineImpl(null, super.engine.getConfig(), EngineType.LIST_CMD);
      resurrect(kickedTo, lastKicked, this.engine.channel, slaveEngine);
      log.info("Executed [kick] command by user: {} - resurrected last kicked user", author);
      return successful();
    }

    log.info("Executed [kick] command by user: {}, no username parameter specified", author);
    return fail("\\n Example: %skick merc".formatted(engine.prefix));
  }

  private void executeKickMode(String mode, List<String> arguments, List<String> activeUsers) {
    switch (mode) {
      case "-m" -> {
        for (int i = 1; i < arguments.size(); i++) {
          String target = arguments.get(i);
          kickUserIfPresent(target, activeUsers);
        }
      }
      case "-c" -> {
        if (arguments.size() < 2) {
          return;
        }
        String value = arguments.get(1);
        List<String> usernames = new java.util.ArrayList<>();
        for (String username : activeUsers) {
          if (username.contains(value)) {
            usernames.add(username);
          }
        }
        log.info("Kicking users: {}", usernames);
        for (String target : usernames) {
          engine.modService.kick(target);
          log.info("Kicked: {}", target);
        }
      }
      default -> kickUserIfPresent(mode, activeUsers);
    }
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

  private List<String> sanitizeArguments(List<String> arguments) {
    List<String> sanitizedArguments = new java.util.ArrayList<>(arguments.size());
    for (String argument : arguments) {
      sanitizedArguments.add(argument.replace("@", ""));
    }
    return sanitizedArguments;
  }

  private List<String> getActiveUserNicks() {
    List<String> activeUsers = new java.util.ArrayList<>(engine.currentChannelUsers.size());
    for (User user : engine.currentChannelUsers) {
      activeUsers.add(user.getNick());
    }
    return activeUsers;
  }
}
