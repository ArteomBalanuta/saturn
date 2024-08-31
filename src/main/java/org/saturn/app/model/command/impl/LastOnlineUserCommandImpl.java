package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Slf4j
@CommandAliases(aliases = {"lastonline", "seen", "last", "online", "lastseen"})
public class LastOnlineUserCommandImpl extends UserCommandBaseImpl {

    private final List<String> aliases = new ArrayList<>();

    public LastOnlineUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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

    /* TODO: doesnt account for user renaming, needs to be fixed. */
    @Override
    public void execute() {
        String author = chatMessage.getNick();
        Optional<String> target = getArguments().stream()
                .findFirst();

        if (target.isEmpty()) {
            engine.outService.enqueueMessageForSending(author, "\\n Example: " + engine.prefix + "lastseen merc", isWhisper());
            log.info("Executed [lastseen] command by user: {}, target: not set", author);
            return;
        }

        String lastSeen = engine.userService.lastOnline(target.get());
        engine.outService.enqueueMessageForSending(author, lastSeen, chatMessage.isWhisper());

        log.info("Executed [lastseen] command by user: {}", author);
    }
}
