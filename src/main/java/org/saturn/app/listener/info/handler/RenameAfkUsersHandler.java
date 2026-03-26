package org.saturn.app.listener.info.handler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.listener.info.InfoMessageContext;
import org.saturn.app.listener.info.InfoMessageHandler;
import org.saturn.app.model.dto.Afk;
import org.saturn.app.model.dto.User;

@Slf4j
public class RenameAfkUsersHandler implements InfoMessageHandler {

  @Override
  public boolean handle(InfoMessageContext context) {
    String text = context.getInfoMessage().getText();
    if (!text.contains(" is now ")) {
      return true;
    }

    String[] split = text.split(" is now ");
    String before = split[0];
    String after = split[1];

    log.warn("User renamed from: {} to {}", before, after);
    if (before.equals(context.getEngine().nick)) {
      context.getEngine().nick = after;
    }

    for (Map.Entry<String, Afk> entry : context.getEngine().afkUsers.entrySet()) {
      List<User> afkUsers = entry.getValue().getUsers();
      Optional<User> afkUser = afkUsers.stream().filter(u -> u.getNick().equals(before)).findFirst();
      if (afkUser.isPresent()) {
        log.warn(
            "User: {} was renamed, updating afk list with new username: {}, accordingly",
            before,
            after);
        afkUser.get().setNick(after);
      }
    }
    return true;
  }
}
