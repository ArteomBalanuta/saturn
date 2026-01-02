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
@CommandAliases(aliases = {"nicks", "t2n"})
public class ListNicksCommandImpl extends UserCommandBaseImpl {

  public ListNicksCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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

    List<String> arguments = getArguments();
    String trip = arguments.getFirst();
    if (trip == null) {
      engine.outService.enqueueMessageForSending(
          author, "Set the trip. Example: " + engine.prefix + "t2n QLnV66", isWhisper());

      return Optional.of(Status.FAILED);
    }

    List<String> nicksByTrip = engine.userService.getNicksByTrip(trip);
    String collected = String.join(",", nicksByTrip);

    engine.outService.enqueueMessageForSending(author, collected, isWhisper());
    log.info("Executed [n2t] command by user: {}, argument: {}", author, arguments);

    return Optional.of(Status.SUCCESSFUL);
  }
}
