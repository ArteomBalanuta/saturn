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
@CommandAliases(aliases = {"authorize", "auth"})
public class AuthorizeTripCommandImpl extends UserCommandBaseImpl {
  public AuthorizeTripCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    final List<String> arguments = getArguments();

    final String author = chatMessage.getNick();
    if (arguments.isEmpty()) {
      log.info("Executed [authorizetrip] command by user: {}, no trip set", author);
      super.engine.outService.enqueueMessageForSending(
          author, " example: *auth cmdTV+", isWhisper());
      return Optional.of(Status.FAILED);
    }

    Optional<String> argument = arguments.stream().findFirst();

    String trip = argument.get();
    engine.modService.auth(trip);
    super.engine.outService.enqueueMessageForSending(
        author, " authorized trip: " + trip, isWhisper());
    log.info("Executed [authorizetrip] command by user: {}, trip: {}}", author, trip);

    return Optional.of(Status.SUCCESSFUL);
  }
}
