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
@CommandAliases(aliases = {"reg", "register"})
public class RegisterUserCommandImpl extends UserCommandBaseImpl {
  public RegisterUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    if (arguments.size() < 2) {
      log.info(
          "Executed [register] command by user: {}, trip: {}, no arguments present",
          author(),
          Optional.ofNullable(chatMessage.getTrip()));
      return failWithUsage("reg merc g0KY09");
    }

    String name = arguments.get(0);
    String trip = arguments.get(1);
    boolean nameRegistered = engine.userService.isNameRegistered(name);
    boolean tripRegistered = engine.userService.isTripRegistered(trip);

    if (!nameRegistered && !tripRegistered) {
      return registerNewIdentity(name, trip);
    }

    if (!nameRegistered) {
      engine.userService.registerNameByTrip(name, trip);
      replyToAuthor("New name: %s, assigned to trip: %s".formatted(name, trip));
      log.info("Executed [register] command by user: {}, arguments: {}", author(), arguments);
      return successful();
    }

    if (!tripRegistered) {
      engine.userService.registerTripByName(name, trip);
      replyToAuthor("New trip: %s, assigned to user named: %s".formatted(trip, name));
      log.info("Executed [register] command by user: {}, arguments: {}", author(), arguments);
      return successful();
    }

    replyToAuthor("Name %s and trip %s are already registered.".formatted(name, trip));
    log.info("Executed [register] command by user: {}, arguments: {}", author(), arguments);
    return Optional.of(Status.FAILED);
  }

  private Optional<Status> registerNewIdentity(String name, String trip) {
    int code = engine.userService.register(name, trip, Role.REGULAR.name());
    if (code == 1) {
      return fail("Something went wrong");
    }

    replyToAuthor("User has been registered successfully, now you can msg him by name: %s".formatted(name));
    return successful();
  }
}
