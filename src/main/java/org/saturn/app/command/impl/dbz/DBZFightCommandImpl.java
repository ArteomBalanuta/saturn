package org.saturn.app.command.impl.dbz;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"dfight", "df"})
public class DBZFightCommandImpl extends UserCommandBaseImpl {
  public DBZFightCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    String enemy = getArguments().getFirst();
    engine.dbzService.fight(enemy);
    engine.dbzService.lvlUp(author);

    engine.outService.enqueueMessageForSending(
        StringEscapeUtils.escapeJava(
            "Gz. Enemy has been slain. Your leveled up! Granted 5 free stats!"));
    log.info("Executed [dbz_fight] command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }
}
