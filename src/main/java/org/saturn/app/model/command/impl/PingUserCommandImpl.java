package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@CommandAliases(aliases = {"ping", "p"})
public class PingUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public PingUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    public void execute() {
        String author = chatMessage.getNick();
        engine.pingService.executePing(author);

        log.info("Executed [ping] command by user: {}, value", author);
    }
}
