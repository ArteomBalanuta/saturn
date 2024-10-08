package org.saturn.app.command.impl.moderator;

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

import static org.saturn.app.util.Util.getAdminTrips;

@Slf4j
@CommandAliases(aliases = {"overflow", "shoot", "love", "hug", "kiss"})
public class OverflowCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public OverflowCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    public Role getAuthorizedRole() {
        return Role.MODERATOR;
    }

    @Override
    public Optional<Status> execute() {
        List<String> arguments = getArguments();
        String author = chatMessage.getNick();

        if (arguments.isEmpty()) {
            super.engine.outService.enqueueMessageForSending(author, "target nick isn't set! Example: "+ engine.prefix + "shoot @merc", isWhisper());
            log.info("Executed [overflow] command by user: {}", author);
            return Optional.of(Status.FAILED);
        }

        Optional<String> argument = arguments.stream().findFirst();

        String target = argument.get().replace("@", "");
        engine.modService.overflow(target);

        log.info("Executed [overflow] command by user: {}, target: {}", author, target);

        return Optional.of(Status.SUCCESSFUL);
    }
}
