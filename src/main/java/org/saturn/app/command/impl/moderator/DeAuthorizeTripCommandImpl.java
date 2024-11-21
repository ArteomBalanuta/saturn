package org.saturn.app.command.impl.moderator;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminTrips;

@Slf4j
@CommandAliases(aliases = {"deauthorize", "deauth"})
public class DeAuthorizeTripCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public DeAuthorizeTripCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getAdminTrips(engine));
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
        return Role.MODERATOR;
    }

    @Override
    public Optional<Status> execute() {
        List<String> arguments = getArguments();

        String author = chatMessage.getNick();
        if (arguments.isEmpty()) {
            log.info("Executed [deauthorizetrip] command by user: {}, no trip set", author);
            super.engine.outService.enqueueMessageForSending(author, " example: *deauth cmdTV+", isWhisper());
            return Optional.of(Status.FAILED);
        }

        Optional<String> argument = arguments.stream().findFirst();

        String trip = argument.get();
        engine.modService.deauth(trip);
        super.engine.outService.enqueueMessageForSending(author, " deauthorized trip: " + trip, isWhisper());
        log.info("Executed [deauthorizetrip] command by user: {}, trip: {}}", author, trip);

        return Optional.of(Status.SUCCESSFUL);

    }
}
