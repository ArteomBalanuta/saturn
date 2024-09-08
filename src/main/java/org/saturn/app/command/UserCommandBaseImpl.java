package org.saturn.app.command;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.DateUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.toLower;

@Slf4j
public class UserCommandBaseImpl implements UserCommand  {
    protected final EngineImpl engine;
    protected ChatMessage chatMessage;
    protected final List<String> authorizedTrips = new ArrayList<>();
    private List<String> aliases;
    private final List<String> arguments = new ArrayList<>();

    public UserCommandBaseImpl(ChatMessage chatMessage, EngineImpl engine, List<String> authorizedTrips) {
        this.engine = engine;
        this.chatMessage = chatMessage;

        this.authorizedTrips.addAll(authorizedTrips);

        String message = chatMessage.getText().substring(engine.getPrefix().length());
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
    public Optional<Status> execute() {
        List<String> aliases = toLower(this.getAliases());
        Optional<UserCommand> cmd = engine.commandFactory.getCommand(this.chatMessage, aliases.get(0));

        if (cmd.isPresent() && engine.authorizationService.isUserAuthorized(cmd.get(), this.chatMessage)) {
            setupArguments(cmd.get());
            String trip = null;
            if (chatMessage.getTrip() != null) {
                trip = chatMessage.getTrip();
            }

            String arguments = cmd.get().getArguments().toString();
            Optional<Status> status = cmd.get().execute();

            engine.logRepository.logCommand(trip, cmd.get().getAliases().toString(), arguments,  status.get().name(), DateUtil.getTimestampNow());
            return status;
        }

        return Optional.empty();
    }

    private void setupArguments(UserCommand cmd) {
        cmd.getArguments().clear();
        cmd.getArguments().addAll(this.arguments);
    }

    @Override
    public List<String> getArguments() {
        String[] array = arguments.toArray(new String[0]);
        if (array[0].contains("\\n")) {
            /* split first argument into array */
            String[] fixedReceiver = StringUtils.splitByWholeSeparator(array[0],"\\n");

            /* nullify broken receiver in initial argument array */
            String[] freshArguments = ArrayUtils.remove(array, 0);

            /* reset arguments with fixed arguments */
            return new ArrayList<>(List.of(ArrayUtils.insert(0, freshArguments, fixedReceiver)));
        }

        return arguments;
    }

    @Override
    public List<String> getAuthorizedTrips() {
        return authorizedTrips;
    }

    @Override
    public Role getAuthorizedRole() {
        return Role.ADMIN; /* least privileged role required by default */
    }

    @Override
    public boolean isWhisper() {
        return chatMessage.isWhisper();
    }
}

