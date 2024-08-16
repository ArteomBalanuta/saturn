package org.saturn.app.command.impl.moderator;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminTrips;

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
    final String author = chatMessage.getNick();
    final Optional<String> target = getArguments().stream().findFirst();
    if (target.isEmpty()) {
      log.info("Executed [ban] command by user: {}, no target set", author);
      engine.outService.enqueueMessageForSending(
          author, "Example: " + engine.prefix + "ban merc", isWhisper());
      return Optional.of(Status.FAILED);
    }
    engine.modService.ban(target.get());
    engine.outService.enqueueMessageForSending(
        author, target.get() + " " + chatMessage.getHash() + " has been banned", isWhisper());

    log.info(
        "Executed [ban] command by user: {}, trip: {}, target: {}",
        author,
        chatMessage.getTrip(),
        target.get());
    return Optional.of(Status.SUCCESSFUL);
  }
}
