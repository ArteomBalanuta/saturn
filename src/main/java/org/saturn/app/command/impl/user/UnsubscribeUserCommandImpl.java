package org.saturn.app.command.impl.user;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

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
@CommandAliases(aliases = {"unsub", "unsubscribe"})
public class UnsubscribeUserCommandImpl extends UserCommandBaseImpl {
  public UnsubscribeUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    String author = chatMessage.getNick();
    String trip = chatMessage.getTrip();
    if (trip == null && !engine.subscribers.contains(trip)) {
      engine.outService.enqueueMessageForSending(
          author,
          "you are not subscribed, please set your trip and use " + engine.prefix + " sub command.",
          false);
      log.info("User: {} failed unsubscribing", author);
      return Optional.of(Status.FAILED);
    }
    engine.subscribers.remove(trip);
    log.info("User: {}, trip: {}, unsubscribed", author, trip);
    engine.outService.enqueueMessageForSending(
        author,
        "your trip will no longer receive hashes and " + "nicks for each new joining user. ",
        true);

    log.info("Executed [unsubscribe] command by user: {}, trip: {}", author, trip);
    return Optional.of(Status.SUCCESSFUL);
  }
}
