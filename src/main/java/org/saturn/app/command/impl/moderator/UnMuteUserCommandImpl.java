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
@CommandAliases(aliases = {"unmute","undumb"})
public class UnMuteUserCommandImpl extends UserCommandBaseImpl {
  public UnMuteUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    final Optional<String> hash = getArguments().stream().findFirst();
    if (hash.isEmpty()) {
      engine.outService.enqueueMessageForSending(
          author, "Example: " + engine.prefix + "unmute jJ4M4fsECSazzlj", isWhisper());
      log.info("Executed [unmute] command by user: {}, no target set", author);
      return Optional.of(Status.FAILED);
    }
    engine.modService.unmute(hash.get());
    engine.outService.enqueueMessageForSending(
        author, hash.get() + " has been unmuted", isWhisper());

    log.info(
        "Executed [unmute] command by user: {}, trip: {}, target: {}",
        author,
        chatMessage.getTrip(),
        hash.get());
    return Optional.of(Status.SUCCESSFUL);
  }
}
