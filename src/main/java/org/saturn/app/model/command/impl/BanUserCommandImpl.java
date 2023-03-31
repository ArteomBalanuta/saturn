package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.User;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class BanUserCommandImpl extends UserCommandBaseImpl {
    public BanUserCommandImpl(EngineImpl engine, List<String> whiteListedTrips) {
        super(null, engine, whiteListedTrips);
        super.setCommandNames(this.getCommandNames());
    }

    @Override
    public List<String> getCommandNames() {
        return List.of("ban");
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
                        .map(User::getHash)
                        .findFirst()
                        .orElse(null))
                .findFirst()
                .ifPresentOrElse(hash -> {
                    engine.modService.ban(nick.get(), hash);
                    engine.outService.enqueueMessageForSending("/whisper @" + author + " banned: " + nick.get() + " hash: " + hash);
                    engine.modService.kick(nick.get());
                }, () -> {
                    /* target isn't in the room */
                    engine.modService.ban(nick.get());
                    engine.outService.enqueueMessageForSending("/whisper @" + author + " banned: " + nick.get());
                });
    }
}
