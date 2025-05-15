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
import org.saturn.app.util.Util;

@Slf4j
@CommandAliases(aliases = {"users", "whitelist", "blacklist", "offenders", "knownoffenders"})
public class PrintNickTripUserCommandImpl extends UserCommandBaseImpl {
  public PrintNickTripUserCommandImpl(
      EngineImpl engine, ChatMessage message, List<String> aliases) {
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
//    String header = "\\n Regular users: \\n ";
    final String result = engine.sqlService.executeFormatted(SQL_PRINT_REGISTERED_USERS);
    engine.outService.enqueueMessageForSending(author, "Users: \\n" + result, isWhisper());

//    String formattedUsers = Util.alignWithWhiteSpace(users, "|", "\u2009", true);
//    engine.outService.enqueueMessageForSending(author, header + formattedUsers, isWhisper());
    log.info("Executed [users] command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }

  public static final String SQL_PRINT_REGISTERED_USERS =
      """
        select distinct t.trip, n.name 
        from trip_names tn 
        inner join names n on tn.name_id  = n.id 
        inner join trips t on tn.trip_id = t.id order by n.name desc;
      """;
}
