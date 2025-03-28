package org.saturn.app.command.impl.user;

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
@CommandAliases(aliases = {"lastonline", "seen", "last", "online", "lastseen"})
public class LastOnlineUserCommandImpl extends UserCommandBaseImpl {
  public LastOnlineUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, List.of("x"));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    String author = chatMessage.getNick();
    Optional<String> target = getArguments().stream().findFirst();

    if (target.isEmpty()) {
      engine.outService.enqueueMessageForSending(
          author, "\\n Example: " + engine.prefix + "lastseen merc", isWhisper());
      log.info("Executed [lastseen] command by user: {}, target: not set", author);
      return Optional.of(Status.FAILED);
    }

    String lastSeen = engine.userService.lastOnline(target.get());
    engine.outService.enqueueMessageForSending(author, lastSeen, chatMessage.isWhisper());

    log.info("Executed [lastseen] command by user: {}", author);
    return Optional.of(Status.SUCCESSFUL);
  }
}
