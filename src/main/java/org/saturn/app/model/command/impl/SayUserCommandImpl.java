package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@Slf4j
@CommandAliases(aliases = {"say", "echo"})
public class SayUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public SayUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getWhiteListedTrips(engine));
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
    public Role getAuthorizedRole() {
        return Role.REGULAR;
    }
    @Override
    public void execute() {
        String author = chatMessage.getNick();
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

        String message = String.valueOf(stringBuilder);

        super.engine.getOutService().enqueueMessageForSending(author, message, isWhisper());
        log.info("Executed [say] command by user: {}, argument: {}", author, message);
    }
}
