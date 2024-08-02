package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"ban", "bb"})
public class BanUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public BanUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    public void execute() {
        List<String> arguments = getArguments();
        String author = super.chatMessage.getNick();

        if (arguments.stream().anyMatch(arg -> arg.equals("-c"))) {
            String pattern = arguments.get(1);
            super.engine.getActiveUsers().stream()
                    .filter(user -> user.getNick().contains(pattern))
                    .forEach(user -> {
                        super.engine.getModService().ban(user.getNick(), user.getHash(), user.getTrip());
                        engine.modService.kick(user.getNick());
                    });
            return;
        }

        String target = getBanningUser(arguments);

        engine.currentChannelUsers.stream()
                .filter(activeUser -> target.equals(activeUser.getNick()))
                .findFirst()
                .ifPresentOrElse(user -> {
                    engine.modService.ban(user.getNick(), user.getTrip(), user.getHash());
                    engine.outService.enqueueMessageForSending("/whisper @" + author + " banned: " + target + "trip: " + user.getTrip() + " hash: " + user.getHash());
                    engine.modService.kick(target);
                }, () -> {
                    /* target isn't in the room */
                    engine.modService.ban(target);
                    engine.outService.enqueueMessageForSending("/whisper @" + author + " banned: " + target);
                });
    }

    private static String getBanningUser(List<String> arguments) {
        return arguments.stream()
                .map(target -> target.replace("@", ""))
                .findAny()
                .get();
    }
}
