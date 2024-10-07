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
@CommandAliases(aliases = {"automove"})
public class AutoMoveUserCommandImpl extends UserCommandBaseImpl {
    private static String DESTINATION_CHANNEL = "lounge";
    public static final List<String> SOURCE_CHANNELS = new ArrayList<>();
    private static boolean AUTO_MOVE_STATUS = false;

    public static String getDestinationChannel() {
        return DESTINATION_CHANNEL;
    }

    public static boolean isAutoMoveStatus() {
        return AUTO_MOVE_STATUS;
    }

    private final List<String> aliases = new ArrayList<>();

    public AutoMoveUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getAdminTrips(engine));
        super.setAliases(this.getAliases());
        this.aliases.addAll(aliases);

        /* Default one */
        SOURCE_CHANNELS.add("purgatory");
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
            engine.outService.enqueueMessageForSending(author, engine.prefix + "automove [on|off]", isWhisper());
            engine.outService.enqueueMessageForSending(author, "Current status: " + AUTO_MOVE_STATUS + " , Source rooms: " + SOURCE_CHANNELS + " , Destination room: " + DESTINATION_CHANNEL, isWhisper());
            engine.outService.enqueueMessageForSending(author, "To set source: hell, destination: heaven - use: " + engine.getPrefix() + "automove hell heaven", isWhisper());
            log.info("Executed [automove] command by user: {} - missing required parameters", author);
            return Optional.of(Status.FAILED);
        }

        String firstArgument = arguments.get(0).trim();
        if (firstArgument.equalsIgnoreCase("on")) {
            AUTO_MOVE_STATUS = true;
            engine.outService.enqueueMessageForSending(author, " " + engine.prefix + "automove is enabled", isWhisper());
        } else if (firstArgument.equalsIgnoreCase("off")) {
            AUTO_MOVE_STATUS = false;
            engine.outService.enqueueMessageForSending(author, " " + engine.prefix + "automove is disabled", isWhisper());
        } else if (arguments.size() == 2) {
            String secondArgument = arguments.get(1).trim();
            SOURCE_CHANNELS.add(firstArgument);
            DESTINATION_CHANNEL = secondArgument;
            engine.outService.enqueueMessageForSending(author, "Set source channel: " + SOURCE_CHANNELS + " , destination channel: " + DESTINATION_CHANNEL + ". Make sure bot's REPLICA is serving source channels.", isWhisper());
        }

        log.info("Executed [automove] command by user: {}, arguments: {}", author, firstArgument);
        return Optional.of(Status.SUCCESSFUL);
    }
}
