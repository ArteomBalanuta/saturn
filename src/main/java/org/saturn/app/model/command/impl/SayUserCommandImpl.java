package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommandBaseImpl;

import java.util.List;
import java.util.Optional;

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
        Optional<String> trip = Optional.ofNullable(chatMessage.getTrip());
        StringBuilder sb  = new StringBuilder();
        if (trip.isPresent() && engine.adminTrips.contains(trip.get())) {
            this.getArguments().forEach(s -> append(sb, s, false));
        } else {
            this.getArguments().forEach(s -> append(sb, s, true));
        }

        super.engine.getOutService().enqueueMessageForSending(sb.toString());
    }

    private StringBuilder append(StringBuilder sb, String value, boolean doReplace) {
        return sb.append(value.replace(doReplace ? "/" : "", "").replace(doReplace ? engine.prefix : "", "")).append(" ");
    }
}
