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
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"overflow", "shoot", "love", "hug", "kiss"})
public class OverflowCommandImpl extends UserCommandBaseImpl {
  public OverflowCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    final List<String> arguments = getArguments();
    final String author = chatMessage.getNick();

    if (arguments.isEmpty()) {
      super.engine.outService.enqueueMessageForSending(
          author, "target nick isn't set! Example: " + engine.prefix + "shoot @merc", isWhisper());
      log.info("Executed [overflow] command by user: {}", author);
      return Optional.of(Status.FAILED);
    }

    Optional<String> argument = arguments.stream().findFirst();
    String target = argument.get().replace("@", "");
    engine.modService.overflow(target);

    log.info("Executed [overflow] command by user: {}, target: {}", author, target);
    return Optional.of(Status.SUCCESSFUL);
  }
}
