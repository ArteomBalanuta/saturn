package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;

import java.util.ArrayList;
import java.util.List;

import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"blacklist", "banlist"})
public class BlacklistUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public BlacklistUserCommandImpl(EngineImpl engine, List<String> aliases) {
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
        engine.modService.listBanned();
    }
}
