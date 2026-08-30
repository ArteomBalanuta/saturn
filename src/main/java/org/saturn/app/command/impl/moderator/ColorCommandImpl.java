package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.IdentityUtil;

@Slf4j
@CommandAliases(
    aliases = {
      "color",
    })
public class ColorCommandImpl extends UserCommandBaseImpl {
  public ColorCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    if (arguments.size() < 2) {
      log.info("Executed [color] command by user: {}, no username parameter specified", author());
      replyToAuthor("\\n Example: %scolor merc 00ff00".formatted(engine.prefix));
      return Optional.of(Status.FAILED);
    }

    String target;
    try {
      target = IdentityUtil.normalizeNickTarget(arguments.getFirst());
    } catch (IllegalArgumentException e) {
      return failWithUsage("color merc 00ff00");
    }
    String color = arguments.get(1);
    if (!isUserActive(target)) {
      replyToAuthor("User %s is not in the room, color was not applied.".formatted(target));
      log.info("User: {} is not in the room, can't apply color", target);
      return Optional.of(Status.FAILED);
    }

    engine.modService.forceColor(target, color);
    log.info("Applied color: {}, to user: {}", color, target);
    log.info("Executed forceColor command by user: {}", author());
    return successful();
  }

  private boolean isUserActive(String target) {
    return engine.currentChannelUsers.stream()
        .map(User::getNick)
        .anyMatch(nick -> IdentityUtil.sameNick(nick, target));
  }
}
