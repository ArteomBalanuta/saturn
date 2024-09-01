package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.ModService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.saturn.app.util.Util.getAdminTrips;

@Slf4j
@CommandAliases(aliases = {"kick", "k", "out"})
public class KickUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public KickUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getAdminTrips(engine));
        super.setAliases(this.getAliases());
        this.aliases.addAll(aliases);
    }

    @Override
    public List<String> getAliases() {
        return aliases;
    }

    @Override
    public List<String> getArguments() {
        return super.getArguments();
    }

    @Override
    public void execute() {
        List<String> arguments = getArguments();
        arguments = arguments.stream().map(arg -> arg.replace("@", "")).collect(Collectors.toList());

        String author = chatMessage.getNick();
        if (arguments.isEmpty()) {
            log.info("Executed [kick] command by user: {}, no username parameter specified", author);
            engine.outService.enqueueMessageForSending(author, "\\n Example: " + engine.prefix + "kick merc", isWhisper());
            return;
        }

        String flag = arguments.get(0);

        List<String> activeUsers = engine.currentChannelUsers.stream().map(User::getNick).collect(Collectors.toList());

        switch (flag) {
            case "-m" -> {
                List<String> usernames = arguments.stream().skip(1).collect(Collectors.toList());
                for (String target : usernames) {
                    kickUserIfPresent(target, activeUsers);
                }
            }
            case "-c" -> {
                String value = arguments.get(1);
                List<String> usernames = activeUsers.stream()
                        .filter(username -> username.contains(value))
                        .collect(Collectors.toList());

                log.info("Kicking users: {}", usernames);

                for (String target : usernames) {
                    engine.modService.kick(target);
                    log.info("Kicked: {}", target);
                }
            }
            default -> kickUserIfPresent(flag, activeUsers);
        }

        log.info("Executed kick command by user: {}", author);
    }

    private void kickUserIfPresent(String target, List<String> activeUsers) {
        if (activeUsers.contains(target)) {
            engine.modService.kick(target);
            log.info("Kicked: {}", target);
        } else {
            log.info("User: {} is not in the room", target);
        }
    }
}
