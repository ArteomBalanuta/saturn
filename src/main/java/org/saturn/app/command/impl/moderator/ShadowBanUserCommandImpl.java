package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.BanRecord;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"shadowban", "sban"})
public class ShadowBanUserCommandImpl extends UserCommandBaseImpl {
  public ShadowBanUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    String author = super.chatMessage.getNick();

    if (arguments.isEmpty()) {
      log.info("Executed [shadow ban] command by user: {}, no target set", author);
      engine.outService.enqueueMessageForSending(
          author, "Example:" + engine.prefix + "shadowban merc", isWhisper());
      return Optional.of(Status.FAILED);
    }

    if (arguments.stream().anyMatch(arg -> arg.equals("-c"))) {
      String pattern = arguments.get(1);
      log.info("Shadow Banning usernames containing following string: {}", pattern);
      List<User> users =
          super.engine.currentChannelUsers.stream()
              .filter(user -> user.getNick().contains(pattern))
              .toList();

      List<String> userNames = users.stream().map(User::getNick).collect(Collectors.toList());
      log.info("Matching users: {}", userNames);

      /* TODO: parse reason */
      users.forEach(
          user -> {
            BanRecord dto = new BanRecord(user.getTrip(), user.getNick(), user.getHash(), null);
            super.engine.modService.shadowBan(dto);
            log.info(
                "Shadow Banned nick: {}, hash: {}, trip: {}",
                user.getNick(),
                user.getHash(),
                user.getTrip());
            engine.modService.kick(user.getNick());
            log.info("User: {}, has been kicked", user.getNick());
          });

      log.info("Executed [shadow ban] command by user: {}", author);
      return Optional.of(Status.SUCCESSFUL);
    }

    String target = getBanningUser(arguments);

    engine.currentChannelUsers.stream()
        .filter(activeUser -> target.equals(activeUser.getNick()))
        .findFirst()
        .ifPresentOrElse(
            user -> {
              BanRecord dto = new BanRecord(user.getTrip(), user.getNick(), user.getHash(), null);
              engine.modService.shadowBan(dto);
              log.warn(
                  "Shadow Banned nick: {}, hash: {}, trip: {}",
                  user.getNick(),
                  user.getHash(),
                  user.getTrip());
              engine.outService.enqueueMessageForSending(
                  author,
                  "banned: " + target + " trip: " + user.getTrip() + " hash: " + user.getHash(),
                  isWhisper());
              engine.modService.kick(target);
              log.info("User: {}, has been kicked", target);
            },
            () -> {
              /* target isn't in the room */
              BanRecord dto = new BanRecord(null, target, null, null);
              engine.modService.shadowBan(dto);
              log.info("Target isn't in the room, banned username: {}", target);
              engine.outService.enqueueMessageForSending(author, "banned: " + target, isWhisper());
            });

    log.info("Executed [shadow ban] command by user: {}", author);
    return Optional.of(Status.SUCCESSFUL);
  }

  private static String getBanningUser(List<String> arguments) {
    return arguments.stream().map(target -> target.replace("@", "")).findAny().get();
  }
}
