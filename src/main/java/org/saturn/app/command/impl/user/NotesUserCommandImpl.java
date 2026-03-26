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
@CommandAliases(aliases = {"notes"})
public class NotesUserCommandImpl extends UserCommandBaseImpl {
  public NotesUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    Optional<String> trip = Optional.ofNullable(chatMessage.getTrip());
    String author = author();

    if (trip.isEmpty()) {
      replyToAuthor("\\n Set your trip first. Example: %snotes".formatted(engine.prefix));
      log.info("Executed [notes] command by user: {}, trip is not present", author);
      return Optional.of(Status.FAILED);
    }

    if (!hasArguments()) {
      engine.noteService.executeListNotes(author, trip.get());
      log.info("Executed [notes] command by user: {}", author);
      return successful();
    }

    String argument = firstArgument().orElseThrow();
    if (isPurgeCommand(argument)) {
      engine.noteService.executeNotesPurge(author, chatMessage.getTrip());
      log.info("Executed [notes purge] command by user: {}", author);
      return successful();
    }

    return Optional.of(Status.FAILED);
  }

  private boolean isPurgeCommand(String argument) {
    return "purge".equals(argument) || "clear".equals(argument);
  }
}
