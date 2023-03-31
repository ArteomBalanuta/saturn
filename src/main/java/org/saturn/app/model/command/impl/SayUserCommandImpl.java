package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommandBaseImpl;

import java.util.List;

public class SayUserCommandImpl extends UserCommandBaseImpl {
    public SayUserCommandImpl(EngineImpl engine, List<String> whiteListedTrips) {
        super(null, engine, whiteListedTrips);
        super.setCommandNames(this.getCommandNames());
    }

    @Override
    public List<String> getCommandNames() {
        return List.of("say","echo");
    }

    @Override
    public List<String> getArguments() {
        return super.getArguments();
    }

    @Override
    public void execute() {
        StringBuilder sb  = new StringBuilder();
        this.getArguments().forEach(s -> sb.append(s.replace("/","").replace(engine.prefix,"")).append(" "));
        super.engine.getOutService().enqueueMessageForSending(sb.toString());
    }
}
