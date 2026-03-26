package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    String author = author();

    if (arguments.isEmpty()) {
      log.info("Executed [shadow ban] command by user: {}, no target set", author);
      return fail("Example:%sshadowban merc".formatted(engine.prefix));
    }

    if (isContainsMode(arguments)) {
      shadowBanUsersMatching(arguments.get(1));
      log.info("Executed [shadow ban] command by user: {}", author);
      return successful();
    }

    String target = normalizeTarget(arguments.getFirst());
    shadowBanSingleTarget(author, target);
    log.info("Executed [shadow ban] command by user: {}", author);
    return successful();
  }

  private boolean isContainsMode(List<String> arguments) {
    return arguments.size() > 1 && arguments.contains("-c");
  }

  private void shadowBanUsersMatching(String pattern) {
    log.info("Shadow Banning usernames containing following string: {}", pattern);
    List<User> matchingUsers = new ArrayList<>();
    for (User user : engine.currentChannelUsers) {
      if (user.getNick().contains(pattern)) {
        matchingUsers.add(user);
      }
    }

    List<String> userNames = new ArrayList<>(matchingUsers.size());
    for (User user : matchingUsers) {
      userNames.add(user.getNick());
      shadowBanPresentUser(user);
    }
    log.info("Matching users: {}", userNames);
  }

  private void shadowBanSingleTarget(String author, String target) {
    for (User activeUser : engine.currentChannelUsers) {
      if (!target.equals(activeUser.getNick())) {
        continue;
      }

      shadowBanPresentUser(activeUser);
      replyToAuthor(
          "shadow_banned: %s trip: %s hash: %s"
              .formatted(target, activeUser.getTrip(), activeUser.getHash()));
      return;
    }

    BanRecord dto = new BanRecord(null, target, null, null);
    engine.modService.shadowBan(dto);
    log.info("Target isn't in the room, banned username: {}", target);
    replyToAuthor("banned: %s".formatted(target));
  }

  private void shadowBanPresentUser(User user) {
    BanRecord dto = new BanRecord(user.getTrip(), user.getNick(), user.getHash(), null);
    engine.modService.shadowBan(dto);
    log.warn(
        "Shadow Banned nick: {}, hash: {}, trip: {}",
        user.getNick(),
        user.getHash(),
        user.getTrip());
    engine.modService.kick(user.getNick());
    log.info("User: {}, has been kicked", user.getNick());
  }

  private String normalizeTarget(String argument) {
    return argument.replace("@", "");
  }
}
