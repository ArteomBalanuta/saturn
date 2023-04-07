package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;

import static org.saturn.app.util.Util.getAdminTrips;

public class KickUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();
    public KickUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getAdminTrips(engine));
        super.setAliases(this.getAliases());
        this.aliases.addAll(aliases);
    }

    @Override
    public List<String> getAliases() {
        return aliases;
    }

    @Override
    public List<String> getArguments() {
        return super.getArguments();
    }

    @Override
    public void execute() {
        List<String> arguments = getArguments();
        String firstArg = arguments.get(0);
        if ("-m".equals(firstArg)) {
            for (int i = 1; i < arguments.size(); i++) {
                super.engine.getModService().kick(arguments.get(i));
            }
        } else {
            super.engine.getModService().kick(firstArg);
        }
    }
}
