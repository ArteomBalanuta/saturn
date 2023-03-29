package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommandBaseImpl;

import java.util.List;

public class SayUserCommandImpl extends UserCommandBaseImpl {
    public SayUserCommandImpl(EngineImpl engine, List<String> whiteListedTrips) {
        super(null, engine, whiteListedTrips);
        super.setCommandName(this.getCommandName());
    }

    @Override
    public String getCommandName() {
        return "say";
    }

    @Override
    public List<String> getArguments() {
        return super.getArguments();
    }

    @Override
    public void execute() {
        StringBuilder sb  = new StringBuilder();
        this.getArguments().forEach(sb::append);
        super.engine.getOutService().enqueueMessageForSending(sb.toString());
    }
}
