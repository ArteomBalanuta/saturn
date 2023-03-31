package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommandBaseImpl;

import java.util.List;

public class SubscribeUserCommandImpl extends UserCommandBaseImpl {
    public SubscribeUserCommandImpl(EngineImpl engine, List<String> whiteListedTrips) {
        super(null, engine, whiteListedTrips);
        super.setCommandNames(this.getCommandNames());
    }

    @Override
    public List<String> getCommandNames() {
        return List.of("sub","subscribe");
    }

    @Override
    public List<String> getArguments() {
        return super.getArguments();
    }

    @Override
    public void execute() {
        String author = chatMessage.getNick();
        engine.subscribers.add(author);
        engine.outService.enqueueMessageForSending("/whisper @" + author + " You will get related hashes, trips and " +
                "nicks whispered for each joining user. You can use :votekick to kick.");
    }
}
