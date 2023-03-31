package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommandBaseImpl;

import java.util.List;

public class KickUserCommandImpl extends UserCommandBaseImpl {
    public KickUserCommandImpl(EngineImpl engine, List<String> whiteListedTrips) {
        super(null, engine, whiteListedTrips);
        super.setCommandNames(this.getCommandNames());
    }

    @Override
    public List<String> getCommandNames() {
        return List.of("kick","k","no","out","sir");
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
