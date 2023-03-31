package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommandBaseImpl;

import java.util.List;

public class InfoUserCommandImpl extends UserCommandBaseImpl {
    public InfoUserCommandImpl(EngineImpl engine, List<String> whiteListedTrips) {
        super(null, engine, whiteListedTrips);
        super.setCommandNames(this.getCommandNames());
    }

    @Override
    public List<String> getCommandNames() {
        return List.of("info","i");
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
                        .ifPresentOrElse(user -> engine.outService.enqueueMessageForSending("/whisper @" + author + " - " +
                                        "\\n User trip: " + user.getTrip() +
                                        "\\n User hash: " + user.getHash()),
                                () -> engine.outService.enqueueMessageForSending("/whisper @" + author + " " +
                                        "\\n User: " + nick + " not found!")));
    }
}
