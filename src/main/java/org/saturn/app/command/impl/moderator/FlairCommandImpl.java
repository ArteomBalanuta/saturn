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

@Slf4j
@CommandAliases(
    aliases = {
      "flair",
    })
public class FlairCommandImpl extends UserCommandBaseImpl {
  public FlairCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    List<String> arguments = getArguments().stream().map(arg -> arg.replace("@", "")).toList();
    if (arguments.size() < 2) {
      log.info("Executed [flair] command by user: {}, no username parameter specified", author());
      replyToAuthor("\\n Example: %sflair merc trusted".formatted(engine.prefix));
      return Optional.of(Status.FAILED);
    }

    String target = arguments.getFirst();
    String flair = arguments.get(1);
    if (!isUserActive(target)) {
      replyToAuthor("User %s is not in the room, flair was not applied.".formatted(target));
      log.info("User: {} is not in the room, can't apply flair", target);
      return Optional.of(Status.FAILED);
    }

    engine.modService.forceFlair(target, flair);
    replyToAuthor("\\n Flair set successfully!");
    log.info("Applied flair: {}, to user: {}", flair, target);
    log.info("Executed forceFlair command by user: {}", author());
    return successful();
  }

  private boolean isUserActive(String target) {
    return engine.currentChannelUsers.stream().map(User::getNick).anyMatch(target::equals);
  }
}
