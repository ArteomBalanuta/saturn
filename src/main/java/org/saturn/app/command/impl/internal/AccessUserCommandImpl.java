package org.saturn.app.command.impl.internal;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@Slf4j
@CommandAliases(aliases = {"grant", "access"})
public class AccessUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public AccessUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    public Optional<Status> execute() {
        Optional<String> trip = Optional.ofNullable(chatMessage.getTrip());
        String author = chatMessage.getNick();

        if (getArguments().size() != 2 || trip.isEmpty()) {
            engine.outService.enqueueMessageForSending(author, "\\n Set your trip first. Example: " + engine.prefix + "grant 8Wotmg ADMIN", isWhisper());
            log.info("Executed [grant] command by user: {}, trip is not present", author);
            return Optional.of(Status.FAILED);
        }

        Role newRole = null;
        try {
            newRole = Role.valueOf(getArguments().get(1));
        } catch (IllegalArgumentException e) {
            log.warn("No such role: {} present.", getArguments().get(1));
            log.info("Executed [grant] command by user: {}, trip: {}", author, trip);
            return Optional.of(Status.FAILED);
        }

        String targetTrip = getArguments().get(0);
        List<String> trips = new ArrayList<>();
        if (targetTrip.contains(",")) {
            trips = List.of(targetTrip.split(","));
        }

        if (trips.isEmpty()) {
            engine.authorizationService.grant(targetTrip, newRole);
            engine.outService.enqueueMessageForSending(author, "\\n Granted new Role: " + newRole.name() + " to trip: " + targetTrip, isWhisper());
        } else {
            trips.forEach(t -> engine.authorizationService.grant(t, Role.USER));
            engine.outService.enqueueMessageForSending(author, "\\n Granted new Roles: " + newRole.name() + " to trips: " + trips, isWhisper());
        }

        log.info("Executed [grant] command by user: {}, trip: {}, new Role: {} for trip: {}", author, trip.get(), newRole.name(), targetTrip);

        return Optional.of(Status.SUCCESSFUL);
    }
}

