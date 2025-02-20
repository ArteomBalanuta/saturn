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
@CommandAliases(aliases = {"say", "echo"})
public class SayUserCommandImpl extends UserCommandBaseImpl {
  public SayUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    Optional<String> trip = Optional.ofNullable(chatMessage.getTrip());

    StringBuilder stringBuilder = new StringBuilder();
    if (trip.isPresent() && List.of(engine.adminTrips.split(",")).contains(trip.get())) {
      this.getArguments().forEach(argument -> stringBuilder.append(argument).append(" "));
    } else {
      this.getArguments()
          .forEach(
              argument -> {
                String sanitizedArgument = argument.replaceAll("[^A-Za-z0-9 ]", "");
                stringBuilder.append(sanitizedArgument).append(" ");
              });
    }

    String message = String.valueOf(stringBuilder);

    engine.outService.enqueueMessageForSending(message);
    log.info("Executed [say] command by user: {}, argument: {}", author, message);

    return Optional.of(Status.SUCCESSFUL);
  }
}
