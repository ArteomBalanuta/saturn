package org.saturn.app.command.impl.user;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@CommandAliases(aliases = {"mail", "msg", "send"})
public class MailUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public MailUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, List.of("x")); /* everyone */
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
        if (getArguments().isEmpty()) {
            log.info("Executed [msg] command by user: {}, no target set", author);
            engine.outService.enqueueMessageForSending(author,"Example: " + engine.prefix + "msg MinusGix doom", isWhisper());
            return Optional.of(Status.FAILED);
        }
        engine.mailService.executeMail(chatMessage, this);
        log.info("Executed [msg] command by user: {}", author);
        return Optional.of(Status.SUCCESSFUL);
    }
}