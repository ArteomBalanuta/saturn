package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;

@CommandAliases(aliases = {"info", "i"})
public class InfoUserCommandImpl extends UserCommandBaseImpl {

    private final List<String> aliases = new ArrayList<>();

    public InfoUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    public void execute() {
        String author = chatMessage.getNick();
        getArguments().stream()
                .findFirst()
                .ifPresent(nick -> engine.currentChannelUsers.stream()
                        .filter(user -> nick.equals(user.getNick()))
                        .findFirst()
                        .ifPresentOrElse(user -> engine.outService.enqueueMessageForSending(author,
                                        "\\n User trip: " + user.getTrip() +
                                                "\\n User hash: " + user.getHash(), isWhisper()),
                                () -> engine.outService.enqueueMessageForSending(author, "\\n User: " + nick + " not found!", isWhisper())));
    }
}
