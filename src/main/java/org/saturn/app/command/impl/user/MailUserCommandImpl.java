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
@CommandAliases(aliases = {"mail", "msg", "send"})
public class MailUserCommandImpl extends UserCommandBaseImpl {
  public MailUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, List.of("x")); /* using '*' is more natural than 'x', refactor later */
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    if (!hasArguments()) {
      log.info("Executed [msg] command by user: {}, no target set", author());
      return failWithUsage("msg MinusGix doom");
    }
    engine.mailService.executeMail(chatMessage, this);
    log.info("Executed [msg] command by user: {}", author());
    return successful();
  }
}
