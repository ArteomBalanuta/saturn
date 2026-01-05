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
@CommandAliases(aliases = {"dbzstr", "dstr", "daddstr"})
public class DBZAddStrCommandImpl extends UserCommandBaseImpl {
  public DBZAddStrCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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

    String stats = getArguments().getFirst();

    int freeStats = engine.dbzService.getFreeStats(author);
    if (freeStats <= 0) {
      engine.outService.enqueueMessageForSending(
          StringEscapeUtils.escapeJava("You don't have free stats. Level up!"));
      return Optional.of(Status.SUCCESSFUL);
    }

    engine.dbzService.addStr(author, Integer.parseInt(stats));

    engine.outService.enqueueMessageForSending(StringEscapeUtils.escapeJava(stats));
    log.info("Executed [dbz_stats] command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }
}
