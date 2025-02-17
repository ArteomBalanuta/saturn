package org.saturn.app.command.impl.moderator;

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
@CommandAliases(aliases = {"reg", "register"})
public class RegisterUserCommandImpl extends UserCommandBaseImpl {
  public RegisterUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getWhiteListedTrips(engine));
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
    if (arguments.size() < 2) {
      log.info(
          "Executed [register] command by user: {}, trip: {}, no arguments present",
          author,
          authorTrip);
      engine.outService.enqueueMessageForSending(
          author, "Example: " + engine.prefix + "reg merc g0KY09", isWhisper());
      return Optional.of(Status.FAILED);
    }

    String name = arguments.get(0);
    String trip = arguments.get(1);

    /* new nick and trip */
    if (!engine.userService.isNameRegistered(name) && !engine.userService.isTripRegistered(trip)) {
      int code = engine.userService.register(name, trip, Role.REGULAR.name());
      if (code == 1) {
        engine.outService.enqueueMessageForSending(author, "Something went wrong", isWhisper());
        return Optional.of(Status.FAILED);
      }

      engine.outService.enqueueMessageForSending(
          author,
          "User has been registered successfully, now you can msg him by name: " + name,
          isWhisper());
    } else if (!engine.userService.isNameRegistered(name)) {
      /* new name, trip exists */
      engine.userService.registerNameByTrip(name, trip);
      engine.outService.enqueueMessageForSending(
          author, "New name has been registered successfully. Name: " + name, isWhisper());
    } else if (!engine.userService.isTripRegistered(name)) {
      /* new trip, nick exists */
      engine.userService.registerTripByName(name, trip);
      engine.outService.enqueueMessageForSending(
          author, "New trip has been registered successfully. Trip: " + trip, isWhisper());
    }

    log.info("Executed [register] command by user: {}, arguments: {}", author, arguments);
    return Optional.of(Status.SUCCESSFUL);
  }
}
