package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;

import java.util.ArrayList;
import java.util.List;

import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"unban", "mercy"})
public class UnbanUserCommandImpl extends UserCommandBaseImpl {

    private final List<String> aliases = new ArrayList<>();

    public UnbanUserCommandImpl(EngineImpl engine, List<String> aliases) {
        super(null, engine, getAdminTrips(engine));
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
        getArguments().stream()
                .findFirst()
                .ifPresent(target -> {
                    engine.modService.unban(target);
                    engine.outService.enqueueMessageForSending("/whisper @" + author + " unbanned " + target);
                });
    }
}
