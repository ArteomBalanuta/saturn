package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@CommandAliases(aliases = {"users","whitelist","blacklist","offenders","knownoffenders"})
public class PrintNickTripUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public PrintNickTripUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, List.of("x"));
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
        return Role.REGULAR;
    }
    @Override
    public Optional<Status> execute() {
        String author = chatMessage.getNick();
        String users = "\\n Registered offenders: \\n merc | 8Wotmg\\n" +
                "wwandrew | pwnuUa\\n" +
                "perge_sequar | VirtuS\\n" +
                "bacon | 4bR/CS\\n" +
                "MinusGix | Rdais/\\n" +
                "ultra_weeb | Rdais/\\n" +
                "nathan | datura\\n" +
                "insane | +/xKF+\\n" +
                "3xi573n7ivli5783vR | ztnKBB\\n" +
                "jetty | 7a5Ev9\\n" +
                "Roslot | Roslot\\n" +
                "0x17 | 6gIBvG \\n" +
                "Rut | rrrrr\\n" +
                "Zed | GODZed\\n" +
                "Cereals | TeaJjh\\n" +
                "jill | Zvoxsl\\n" +
                "Regret_ | cmdTV+\\n" +
                "AnnikaV9 | hACkeR\\n" +
                "usv2 | hACkeR\\n" +
                "Meth | Methjw\\n" +
                "xyz | XYZ+bX\\n" +
                "titZ_beta | nntitZ\\n" +
                "EntertainmentOne | XalBBq\\n" +
                "lol | xhvbdp";

        engine.outService.enqueueMessageForSending(author,users, isWhisper());
        log.info("Executed [users] command by user: {}", author);

        return Optional.of(Status.SUCCESSFUL);
    }
}
