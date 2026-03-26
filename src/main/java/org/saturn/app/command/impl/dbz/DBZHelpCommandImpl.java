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
@CommandAliases(aliases = {"dbzhelp", "dbz", "dhelp"})
public class DBZHelpCommandImpl extends UserCommandBaseImpl {
  public DBZHelpCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    Optional<String> trip = Optional.ofNullable(chatMessage.getTrip());

    String dbzhelp =
        """
This is a DBZ universe text based game.
Main mechanics:
/train, - training your char in order to level up and gain point (stats)
/fight <nick>, - fight against a player
/claim - claim an item that just spawned
\u2009
/stats - displays character stats
/strength <int> - add a point into str
/agility <int> - add a point into agility
/vitality <int> - add a point into vitality
/energy <int> - add a point into energy
""";

    engine.outService.enqueueMessageForSending(StringEscapeUtils.escapeJava(dbzhelp));
    log.info("Executed [dbzhelp] command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }
}
