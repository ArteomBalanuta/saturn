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
      "flair",
    })
public class FlairCommandImpl extends UserCommandBaseImpl {
  public FlairCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
      log.info("Executed [flair] command by user: {}, no username parameter specified", author);
      engine.outService.enqueueMessageForSending(
          author, "\\n Example: " + engine.prefix + "flair merc", isWhisper());
      return Optional.of(Status.FAILED);
    }

    String target = arguments.getFirst();
    String flair = arguments.get(1);
    List<String> activeUsers = engine.currentChannelUsers.stream().map(User::getNick).toList();

    if (activeUsers.contains(target)) {
      engine.modService.forceFlair(target, flair);
      log.info("Applied flair: {}, to user: {}", flair, target);
    } else {
      log.info("User: {} is not in the room, can't apply flair", target);
    }

    engine.outService.enqueueMessageForSending(author, "\\n Flair set successfully!", isWhisper());

    log.info("Executed forceFlair command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }
}
