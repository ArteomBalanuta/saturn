package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CommandAliases(aliases = {"say", "echo"})
public class SayUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public SayUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, List.of("x"));
        super.setAliases(this.getAliases());
        this.aliases.addAll(aliases);
    }

    @Override
    public List<String> getAliases() {
        return this.aliases;
    }
    @Override
    public List<String> getArguments() {
        return super.getArguments();
    }

    @Override
    public void execute() {
        Optional<String> trip = Optional.ofNullable(chatMessage.getTrip());
        StringBuilder stringBuilder = new StringBuilder();
        if (trip.isPresent() && engine.adminTrips.contains(trip.get())) {
            this.getArguments().forEach(argument -> stringBuilder.append(argument).append(" ")) ;
        } else {
            this.getArguments().forEach(argument -> {
                String sanitizedArgument = argument.replaceAll("[^A-Za-z0-9 ]", "");
                stringBuilder.append(sanitizedArgument).append(" ");
            });
        }

        super.engine.getOutService().enqueueMessageForSending(stringBuilder.toString());
    }
}
