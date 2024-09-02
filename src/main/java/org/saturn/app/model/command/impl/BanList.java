package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;

import static org.saturn.app.util.Util.getAdminTrips;

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
        return Role.REGULAR;
    }
    public void execute() {
        String author = chatMessage.getNick();
        engine.modService.listBanned(author);
        log.info("Executed [banlist] command by user: {}", author);
    }
}