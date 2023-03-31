package org.saturn.app.model.command;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.ChatMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.saturn.app.util.Util.toLower;

public class UserCommandBaseImpl implements UserCommand {
    protected final EngineImpl engine;
    protected ChatMessage chatMessage;
    protected final List<String> whiteListedTrips = new ArrayList<>();
    private List<String> commandName;
    private final List<String> arguments = new ArrayList<>();

    public UserCommandBaseImpl(ChatMessage chatMessage, EngineImpl engine, List<String> allowedTrips) {
        this.engine = engine;
        this.chatMessage = chatMessage;

        this.whiteListedTrips.addAll(allowedTrips);

        /* It is null for specific user commands */
        if (chatMessage == null) {
            return;
        }

        String message = chatMessage.getText().substring(engine.prefix.length());
        if (message.contains(" ")) {
            setCommandNames(List.of(message.substring(0, message.indexOf(" ")).trim().toUpperCase()));
            parseArguments(message);
            return;
        }

        setCommandNames(List.of(message.trim().toUpperCase()));
    }

    private void parseArguments(String message) {
        String arguments = message.substring(message.indexOf(" ") + 1);
        if (arguments.contains(" ")) {
            this.arguments.addAll(Arrays.asList(arguments.split(" ")));
        } else {
            this.arguments.add(arguments);
        }
    }

    protected void setCommandNames(List<String> commandName) {
        this.commandName = commandName;
    }

    @Override
    public List<String> getCommandNames() {
        return this.commandName;
    }

    @Override
    public void execute() {
        this.engine.getEnabledCommands()
                .stream()
                .filter(command -> new HashSet<>(toLower(command.getCommandNames())).containsAll(toLower(this.getCommandNames())))
                .findFirst()
                .ifPresentOrElse(cmd -> {
                            setupArguments(cmd);
                            cmd.setChatMessage(chatMessage);
                            if (!isUserAuthorized(cmd, this.chatMessage)) {
                                return;
                            }
                            cmd.execute();
                        },
                        () -> System.out.println("Cant find command: " + this.commandName));
    }

    private void setupArguments(UserCommand cmd) {
        cmd.getArguments().clear();
        cmd.getArguments().addAll(this.arguments);
    }

    private boolean isUserAuthorized(UserCommand userCommand, ChatMessage chatMessage) {
        List<String> whiteTrips = userCommand.getWhiteTrips();
        boolean isWhitelisted = whiteTrips.contains(chatMessage.getTrip()) || whiteTrips.contains("x");
        if (!isWhitelisted) {
            this.engine.getOutService().enqueueMessageForSending("Access denied.");
            return false;
        }
        return true;
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
    public void setChatMessage(ChatMessage message) {
        this.chatMessage = message;
    }
}

