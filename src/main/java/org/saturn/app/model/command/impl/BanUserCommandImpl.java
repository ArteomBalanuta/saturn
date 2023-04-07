package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"ban","bb"})
public class BanUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();
    public BanUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
        String author = super.chatMessage.getNick();

        List<String> arguments = getArguments();
        AtomicReference<String> nick = new AtomicReference<>();
        arguments.stream()
                .map(target -> target.replace("@", ""))
                .peek(target -> nick.set(target))
                .map(target -> engine.currentChannelUsers.stream()
                        .filter(activeUser -> target.equals(activeUser.getNick()))
                        .findFirst()
                        .orElse(null))
                .findFirst()
                .ifPresentOrElse(user -> {
                    engine.modService.ban(user.getNick(), user.getTrip(), user.getHash());
                    engine.outService.enqueueMessageForSending("/whisper @" + author + " banned: " + nick.get() + "trip: " + user.getTrip() +  " hash: " + user.getHash());
                    engine.modService.kick(nick.get());
                }, () -> {
                    /* target isn't in the room */
                    engine.modService.ban(nick.get());
                    engine.outService.enqueueMessageForSending("/whisper @" + author + " banned: " + nick.get());
                });
    }
}
