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
@CommandAliases(aliases = {"unban"})
public class UnBanUserCommandImpl extends UserCommandBaseImpl {
  public UnBanUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
      log.info("Executed [unban] command by user: {}, no hash set", author);
      engine.outService.enqueueMessageForSending(
          author, "Example: " + engine.prefix + "unban HjkUEWNlIRH35Xk", isWhisper());
      return Optional.of(Status.FAILED);
    }
    engine.modService.unban(target.get());
    engine.outService.enqueueMessageForSending(
        author, target.get() + " " + chatMessage.getHash() + " has been unbanned", isWhisper());

    log.info(
        "Executed [unban] command by user: {}, trip: {}, hash: {}",
        author,
        chatMessage.getTrip(),
        target.get());
    return Optional.of(Status.SUCCESSFUL);
  }
}
