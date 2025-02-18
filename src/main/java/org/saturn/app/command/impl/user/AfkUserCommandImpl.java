package org.saturn.app.command.impl.user;

import static org.saturn.app.util.Util.listToString;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.Afk;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"afk", "a"})
public class AfkUserCommandImpl extends UserCommandBaseImpl {
  public AfkUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, List.of("x"));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    final String author = chatMessage.getNick();
    final String trip = chatMessage.getTrip();
    if (trip == null) {
      log.info("Executed [afk] command by user: {} - no trip provided", author);
      engine.outService.enqueueMessageForSending(
          author, "Set your trip in order to use this command", isWhisper());
      return Optional.of(Status.FAILED);
    }

    List<User> afkUsers =
        engine.currentChannelUsers.stream()
            .filter(u -> u.getTrip().equals(trip))
            .collect(Collectors.toList());
    String reason = listToString(getArguments());
    engine.afkUsers.put(trip, new Afk(afkUsers, reason, ZonedDateTime.now(ZoneId.of("UTC")), trip));
    log.info(
        "User: {} executed afk command - marked as afk, using trip: {}, reason: {}",
        author,
        trip,
        reason);

    engine.outService.enqueueMessageForSending(author, " is afk", isWhisper());
    return Optional.of(Status.SUCCESSFUL);
  }
}
