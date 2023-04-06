package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;

import java.util.ArrayList;
import java.util.List;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@CommandAliases(aliases = {"sub", "subscribe"})
public class SubscribeUserCommandImpl extends UserCommandBaseImpl {

    private final List<String> aliases = new ArrayList<>();

    public SubscribeUserCommandImpl(EngineImpl engine, List<String> aliases) {
        super(null, engine, getWhiteListedTrips(engine));
        super.setCommandNames(this.getCommandNames());
        this.aliases.addAll(aliases);
    }

    @Override
    public List<String> getCommandNames() {
        return this.aliases;
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
