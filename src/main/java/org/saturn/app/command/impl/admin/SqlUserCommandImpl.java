package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getAdminTrips;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.text.StringEscapeUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@CommandAliases(aliases = {"sql"})
public class SqlUserCommandImpl extends UserCommandBaseImpl {
  public SqlUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Optional<Status> execute() {
    String cmd = chatMessage.getText();
    String result = engine.sqlService.executeSql(cmd, true);

    engine.outService.enqueueMessageForSending(
        chatMessage.getNick(),
        StringEscapeUtils.escapeJava("Result: \n" + result.replace("\\n", "\n")),
        isWhisper());
    return Optional.of(Status.SUCCESSFUL);
  }
}
