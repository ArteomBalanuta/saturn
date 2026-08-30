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
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.IdentityUtil;

@Slf4j
@CommandAliases(aliases = {"mute", "dumb"})
public class MuteUserCommandImpl extends UserCommandBaseImpl {
  public MuteUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    final String author = author();
    final Optional<String> target = normalizedNickArgument(0, "mute merc");
    if (target.isEmpty()) {
      log.info("Executed [mute] command by user: {}, no target set", author);
      return Optional.of(Status.FAILED);
    }

    Optional<String> hash =
        engine.currentChannelUsers.stream()
            .filter(u -> IdentityUtil.sameNick(u.getNick(), target.get()))
            .map(u -> u.getHash())
            .findFirst();
    if (hash.isEmpty()) {
      log.info(
          "Executed [mute] command by user: {}, target missing from room: {}",
          author,
          target.get());
      return fail(target.get() + " is not in the room");
    }

    engine.modService.mute(target.get());
    replyToAuthor(target.get() + " " + hash.get() + " has been muted");

    log.info(
        "Executed [mute] command by user: {}, trip: {}, target: {}",
        author,
        chatMessage.getTrip(),
        target.get());
    return successful();
  }
}
