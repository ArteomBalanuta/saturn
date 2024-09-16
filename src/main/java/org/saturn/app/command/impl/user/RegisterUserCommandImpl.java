package org.saturn.app.command.impl.user;

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

import static org.saturn.app.util.Util.getWhiteListedTrips;

@Slf4j
@CommandAliases(aliases = {"reg", "register"})
public class RegisterUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();
    public RegisterUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
        return Role.MODERATOR;
    }
    @Override
    public Optional<Status> execute() {
        String author = chatMessage.getNick();
        Optional<String> authorTrip = Optional.ofNullable(chatMessage.getTrip());

        List<String> arguments = getArguments();
        if (arguments.size() < 2) {
            log.info("Executed [register] command by user: {}, trip: {}, no arguments present", author, authorTrip);
            engine.outService.enqueueMessageForSending(author,"Example: " + engine.prefix + "reg merc g0KY09", isWhisper());
            return Optional.of(Status.FAILED);
        }

        String name = arguments.get(0);
        String trip = arguments.get(1);

        String register = engine.userService.register(name, trip, Role.REGULAR.name());
        engine.outService.enqueueMessageForSending(author, "Rows inserted:"  + register + ". User has been registered successfully, now you can msg him by nick: " + name , isWhisper());
        log.info("Executed [register] command by user: {}, arguments: {}", author, arguments);

        return Optional.of(Status.SUCCESSFUL);
    }
}
