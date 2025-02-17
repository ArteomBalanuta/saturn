package org.saturn.app.command.impl.user;

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
    String header = "\\n Regular users: \\n ";
    String users =
        "merc | 8Wotmg\\n"
            + "wwandrew | pwnuUa\\n"
            + "perge_sequar | VirtuS\\n"
            + "bacon | 4bR/CS\\n"
            + "MinusGix | Rdais/\\n"
            + "ultra_weeb | Rdais/\\n"
            + "nathan | datura\\n"
            + "insane | +/xKF+\\n"
            + "3xi573n7ivli5783vR | ztnKBB\\n"
            + "jetty | 7a5Ev9\\n"
            + "Roslot | Roslot\\n"
            + "0x17 | 6gIBvG \\n"
            + "Rut | //////\\n"
            + "Zed | GODZed\\n"
            + "Cereals | TeaJjh\\n"
            + "jill | Zvoxsl\\n"
            + "Regret_ | cmdTV+\\n"
            + "AnnikaV9 | hACkeR\\n"
            + "usv2 | hACkeR\\n"
            + "Meth | Methjw\\n"
            + "xyz | XYZ+bX\\n"
            + "titZ_beta | nntitZ\\n"
            + "EntertainmentOne | XalBBq\\n"
            + "lol | xhvbdp";

    String formattedUsers = Util.alignWithWhiteSpace(users, "|", "\u2009", true);
    engine.outService.enqueueMessageForSending(author, header + formattedUsers, isWhisper());
    log.info("Executed [users] command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }
}
