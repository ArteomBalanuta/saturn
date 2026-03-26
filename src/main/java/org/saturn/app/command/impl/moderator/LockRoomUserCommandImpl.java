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
@CommandAliases(aliases = {"lock", "lockroom"})
public class LockRoomUserCommandImpl extends UserCommandBaseImpl {
  public LockRoomUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
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
      log.info("Executed [lock] command by user: {}, flag: not set", author());
      replyToAuthor("%slock [on|off]".formatted(engine.prefix));
      return Optional.of(Status.FAILED);
    }

    String argument = arguments.getFirst();
    if ("on".equals(argument)) {
      engine.modService.lock();
      replyToAuthor(" Room locked!");
      return successful();
    }

    if ("off".equals(argument)) {
      engine.modService.unlock();
      replyToAuthor(" Room unlocked!");
      return successful();
    }

    replyToAuthor("%slock [on|off]".formatted(engine.prefix));
    return Optional.of(Status.FAILED);
  }
}
