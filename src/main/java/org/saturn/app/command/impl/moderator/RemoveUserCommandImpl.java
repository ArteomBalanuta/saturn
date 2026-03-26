package org.saturn.app.command.impl.moderator;

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
@CommandAliases(aliases = {"del", "delete", "remove"})
public class RemoveUserCommandImpl extends UserCommandBaseImpl {
  public RemoveUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    final String author = chatMessage.getNick();
    final Optional<String> authorTrip = Optional.ofNullable(chatMessage.getTrip());

    List<String> arguments = getArguments();
    if (arguments.isEmpty()) {
      log.info(
          "Executed [remove] command by user: {}, trip: {}, no arguments present",
          author,
          authorTrip);
      engine.outService.enqueueMessageForSending(
          author, "Example: " + engine.prefix + "remove [merc|g0KY09]", isWhisper());
      return Optional.of(Status.FAILED);
    }

    String value = arguments.getFirst();

    if (engine.userService.isNameRegistered(value) || engine.userService.isTripRegistered(value)) {
      int code = engine.userService.delete(value, value);
      if (code == 1) {
        engine.outService.enqueueMessageForSending(
            author, "Something went wrong deleting the user", isWhisper());
        return Optional.of(Status.FAILED);
      }

      engine.outService.enqueueMessageForSending(
          author, "User has been removed successfully", isWhisper());
    }

    log.info("Executed [remove] command by user: {}, arguments: {}", author, arguments);
    return Optional.of(Status.SUCCESSFUL);
  }
}
