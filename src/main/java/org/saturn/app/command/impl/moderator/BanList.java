package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

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
@CommandAliases(aliases = {"banlist", "bannedusers"})
public class BanList extends UserCommandBaseImpl {
  private final List<String> aliases = new ArrayList<>();

  public BanList(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(this.getAliases());
    this.aliases.addAll(aliases);
  }

  @Override
  public List<String> getAliases() {
    return this.aliases;
  }

  @Override
  public List<String> getArguments() {
    return super.getArguments();
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  public Optional<Status> execute() {
    engine.modService.listBanned(chatMessage);
    log.info("Executed [banlist] command by user: {}", chatMessage.getNick());
    return Optional.of(Status.SUCCESSFUL);
  }
}
