package org.saturn.app.command.impl.user;

import static org.saturn.app.util.Util.getWhiteListedTrips;

import java.util.ArrayList;
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
@CommandAliases(aliases = {"sub", "subscribe"})
public class SubscribeUserCommandImpl extends UserCommandBaseImpl {
  public SubscribeUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getWhiteListedTrips(engine));
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
    if (trip == null) {
      engine.outService.enqueueMessageForSending(
          author, "you have to set your trip to use this command.", false);
      log.info("User: {} failed subscription - trip is not set", author);
      return Optional.of(Status.FAILED);
    }
    engine.subscribers.add(trip);
    log.info("User: {}, trip: {}, subscribed for joining users data - hashes, nicks", author, trip);
    engine.outService.enqueueMessageForSending(
        author,
        "your trip will be whispered hashes and " + "nicks for each new joining user. ",
        true);

    log.info("Executed [subscribe] command by user: {}, trip: {}", author, trip);
    return Optional.of(Status.SUCCESSFUL);
  }
}
