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
@CommandAliases(aliases = {"unbanall","pardonall"})
public class UnBanAllUserCommandImpl extends UserCommandBaseImpl {
  public UnBanAllUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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

    engine.modService.unbanAll();
    engine.outService.enqueueMessageForSending(
        author, "mercy.", isWhisper());

    log.info(
        "Executed [unbanall] command by user: {}, trip: {}",
        author,
        chatMessage.getTrip());
    return Optional.of(Status.SUCCESSFUL);
  }
}
