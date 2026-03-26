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

@Slf4j
@CommandAliases(aliases = {"ban"})
public class BanUserCommandImpl extends UserCommandBaseImpl {
  public BanUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    final Optional<String> target = firstArgument();
    if (target.isEmpty()) {
      log.info("Executed [ban] command by user: {}, no target set", author);
      return failWithUsage("ban merc");
    }
    engine.modService.ban(target.get());
    replyToAuthor(target.get() + " has been banned");

    log.info(
        "Executed [ban] command by user: {}, trip: {}, target: {}",
        author,
        chatMessage.getTrip(),
        target.get());
    return successful();
  }
}
