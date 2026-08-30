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
    List<String> arguments = getArguments();
    if (arguments.isEmpty()) {
      log.info(
          "Executed [remove] command by user: {}, trip: {}, no arguments present",
          author(),
          Optional.ofNullable(chatMessage.getTrip()));
      return failWithUsage("remove [merc|g0KY09]");
    }

    String value = arguments.getFirst().trim();
    int code = engine.userService.deleteByNameOrTrip(value);
    if (code == 1) {
      return fail("Something went wrong deleting the user");
    }

    replyToAuthor("User has been removed successfully");
    log.info("Executed [remove] command by user: {}, arguments: {}", author(), arguments);
    return successful();
  }
}
