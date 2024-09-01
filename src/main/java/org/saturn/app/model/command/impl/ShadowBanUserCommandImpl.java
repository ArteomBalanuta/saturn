package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.saturn.app.util.Util.getAdminTrips;

@Slf4j
@CommandAliases(aliases = {"shadowban", "sban"})
public class ShadowBanUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public ShadowBanUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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

        log.info("Executing [shadow ban] command by user: {}", author);

        if (arguments.isEmpty()) {
            log.info("Executed [shadow ban] command by user: {}, no target set", author);
            engine.outService.enqueueMessageForSending(author,"Example:" + engine.prefix + "shadowban merc", isWhisper());
            return;
        }

        if (arguments.stream().anyMatch(arg -> arg.equals("-c"))) {
            String pattern = arguments.get(1);
            log.info("Shadow Banning usernames containing following string: {}", pattern);
            List<User> users = super.engine.getActiveUsers().stream()
                    .filter(user -> user.getNick().contains(pattern))
                    .collect(Collectors.toList());

            List<String> userNames = users.stream().map(User::getNick).collect(Collectors.toList());
            log.info("Matching users: {}", userNames);

            users.forEach(user -> {
                super.engine.getModService().shadowBan(user.getNick(), user.getHash(), user.getTrip());
                log.info("Shadow Banned nick: {}, hash: {}, trip: {}", user.getNick(), user.getHash(), user.getTrip());
                engine.modService.kick(user.getNick());
                log.info("User: {}, has been kicked", user.getNick());
            });
            return;
        }

        String target = getBanningUser(arguments);

        engine.currentChannelUsers.stream()
                .filter(activeUser -> target.equals(activeUser.getNick()))
                .findFirst()
                .ifPresentOrElse(user -> {
                    engine.modService.shadowBan(user.getNick(), user.getTrip(), user.getHash());
                    log.warn("Shadow Banned nick: {}, hash: {}, trip: {}", user.getNick(), user.getHash(), user.getTrip());
                    engine.outService.enqueueMessageForSending(author," banned: " + target + "trip: " + user.getTrip() + " hash: " + user.getHash(), isWhisper());
                    engine.modService.kick(target);
                    log.info("User: {}, has been kicked", target);
                }, () -> {
                    /* target isn't in the room */
                    engine.modService.shadowBan(target);
                    log.info("Target isn't in the room, banned username: {}", target);
                    engine.outService.enqueueMessageForSending(author," banned: " + target, isWhisper());
                });

        log.info("Executed [shadow ban] command by user: {}", author);
    }

    private static String getBanningUser(List<String> arguments) {
        return arguments.stream()
                .map(target -> target.replace("@", ""))
                .findAny()
                .get();
    }
}
