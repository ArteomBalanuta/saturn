package org.saturn.app.model.command;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.toLower;

@Slf4j
public class UserCommandBaseImpl implements UserCommand  {
    protected final EngineImpl engine;
    protected ChatMessage chatMessage;
    protected final List<String> whiteListedTrips = new ArrayList<>();
    private List<String> aliases;
    private final List<String> arguments = new ArrayList<>();

    public UserCommandBaseImpl(ChatMessage chatMessage, EngineImpl engine, List<String> allowedTrips) {
        this.engine = engine;
        this.chatMessage = chatMessage;

        this.whiteListedTrips.addAll(allowedTrips);

        String message = chatMessage.getText().substring(engine.prefix.length());
        if (message.contains(" ")) {
            setAliases(List.of(message.substring(0, message.indexOf(" ")).trim().toUpperCase()));
            parseArguments(message);
            return;
        }

        setAliases(List.of(message.trim().toUpperCase()));
    }

    private void parseArguments(String message) {
        String arguments = message.substring(message.indexOf(" ") + 1);
        if (arguments.contains(" ")) {
            this.arguments.addAll(Arrays.asList(arguments.split(" ")));
        } else {
            this.arguments.add(arguments);
        }
    }

    protected void setAliases(List<String> aliases) {
        this.aliases = aliases;
    }

    @Override
    public List<String> getAliases() {
        return this.aliases;
    }

    @Override
    public void execute() {
        List<String> aliases = toLower(this.getAliases());
        Optional<UserCommand> cmd = engine.commandFactory.getCommand(this.chatMessage, aliases.get(0));

        if (cmd.isPresent() && isUserAuthorized(cmd.get(), this.chatMessage)) {
            setupArguments(cmd.get());
            cmd.get().execute();
        }
    }

    private boolean isUserAuthorized(UserCommand userCommand, ChatMessage chatMessage) {
        List<String> whiteTrips = userCommand.getWhiteTrips();
        boolean isWhitelisted = whiteTrips.contains(chatMessage.getTrip()) || whiteTrips.contains("x");
        if (!isWhitelisted) {
            log.warn("user: {}, trip: {}, [is not] whitelisted", chatMessage.getNick(), chatMessage.getTrip());
            this.engine.getOutService().enqueueMessageForSending(chatMessage.getNick(), " msg mercury for access.", isWhisper());
            return false;
        }

        log.warn("user: {}, trip: {}, [is] whitelisted", chatMessage.getNick(), chatMessage.getTrip());
        return true;
    }

    private void setupArguments(UserCommand cmd) {
        cmd.getArguments().clear();
        cmd.getArguments().addAll(this.arguments);
    }

    @Override
    public List<String> getArguments() {
        return arguments;
    }

    @Override
    public List<String> getWhiteTrips() {
        return whiteListedTrips;
    }

    @Override
    public boolean isWhisper() {
        return chatMessage.isWhisper();
    }
}

