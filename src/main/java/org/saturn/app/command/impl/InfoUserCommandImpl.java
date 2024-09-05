package org.saturn.app.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@CommandAliases(aliases = {"info", "i", "whois", "who"})
public class InfoUserCommandImpl extends UserCommandBaseImpl {

    private final List<String> aliases = new ArrayList<>();

    public InfoUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, List.of("x"));
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
        return Role.REGULAR;
    }

    @Override
    public Optional<Status> execute() {
        String author = chatMessage.getNick();
        Optional<String> nick = getArguments().stream()
                .findFirst();

        if (nick.isEmpty()) {
            log.info("Executed [info] command by user: {}, target: not set", author);
            engine.outService.enqueueMessageForSending(author, "\\n Example: " + engine.prefix + "info merc", isWhisper());
            return Optional.of(Status.FAILED);
        }

        Optional<User> user = engine.currentChannelUsers.stream()
                .filter(u -> nick.get().equals(u.getNick()))
                .findFirst();

        if (user.isEmpty()) {
            engine.outService.enqueueMessageForSending(author, "\\n target with nick:  " + nick.get() + " not found!", isWhisper());
            log.info("Executed [info] command by user: {}, target: {} is not in the room", author, nick.get());
            return Optional.of(Status.FAILED);
        }

        engine.outService.enqueueMessageForSending(author,
                "\\n User trip: " + user.get().getTrip() +
                        "\\n User hash: " + user.get().getHash(), isWhisper());

        log.info("Executed [info] command by user: {}, target: {}", author, nick.get());
        return Optional.of(Status.SUCCESSFUL);
    }
}
