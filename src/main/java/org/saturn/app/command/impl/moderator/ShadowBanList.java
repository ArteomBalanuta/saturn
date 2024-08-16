package org.saturn.app.command.impl.moderator;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminTrips;

@Slf4j
@CommandAliases(aliases = {"shadowbanlist","banlist", "bannedusers"})
public class ShadowBanList extends UserCommandBaseImpl {
  public ShadowBanList(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  public Optional<Status> execute() {
    engine.modService.listShadowBanned(chatMessage);
    log.info("Executed [banlist] command by user: {}", chatMessage.getNick());
    return Optional.of(Status.SUCCESSFUL);
  }
}
