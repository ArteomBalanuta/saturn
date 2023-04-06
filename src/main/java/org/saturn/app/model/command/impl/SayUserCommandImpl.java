package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CommandAliases(aliases = {"say", "echo"})
public class SayUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public SayUserCommandImpl(EngineImpl engine, List<String> aliases) {
        super(null, engine, List.of("x"));
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
