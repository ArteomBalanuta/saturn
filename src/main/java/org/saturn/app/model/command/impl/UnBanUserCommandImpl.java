package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminTrips;

@Slf4j
@CommandAliases(aliases = {"unban"})
public class UnBanUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public UnBanUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    public void execute() {
        String author = chatMessage.getNick();

        Optional<String> target = getArguments().stream().findFirst();
        if (target.isEmpty()) {
            log.info("Executed [unban] command by user: {}, no target set", author);
            engine.outService.enqueueMessageForSending(author,"Example: " + engine.prefix + "unban merc", isWhisper());
            return;
        }
        engine.modService.unban(target.get());
        engine.outService.enqueueMessageForSending(author,target.get() + " " + chatMessage.getHash() + " has been unbanned", isWhisper());

        log.info("Executed [unban] command by user: {}, trip: {}, target: {}", author, chatMessage.getTrip(), target.get());
    }
}
