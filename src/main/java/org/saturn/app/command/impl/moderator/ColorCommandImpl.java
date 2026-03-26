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
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(
    aliases = {
      "color",
    })
public class ColorCommandImpl extends UserCommandBaseImpl {
  public ColorCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    final List<String> arguments =
        getArguments().stream().map(arg -> arg.replace("@", "")).toList();

    final String author = chatMessage.getNick();
    if (arguments.size() < 2) {
      log.info("Executed [color] command by user: {}, no username parameter specified", author);
      engine.outService.enqueueMessageForSending(
          author, "\\n Example: " + engine.prefix + "color merc 00ff00", isWhisper());
      return Optional.of(Status.FAILED);
    }

    String target = arguments.getFirst();
    String color = arguments.get(1);
    List<String> activeUsers = engine.currentChannelUsers.stream().map(User::getNick).toList();

    if (activeUsers.contains(target)) {
      engine.modService.forceColor(target, color);
      log.info("Applied color: {}, to user: {}", color, target);
    } else {
      log.info("User: {} is not in the room, can't apply color", target);
    }

    log.info("Executed forceColor command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }
}
