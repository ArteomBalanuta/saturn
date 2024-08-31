package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;

import static org.saturn.app.util.Util.getAdminTrips;

@Slf4j
@CommandAliases(aliases = {"unban", "mercy"})
public class UnbanUserCommandImpl extends UserCommandBaseImpl {

    private final List<String> aliases = new ArrayList<>();

    public UnbanUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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


    // TODO: fix the bag where unbanall doesnt unban hashes with escape characters..
    @Override
    public void execute() {
        List<String> arguments = getArguments();
        String author = chatMessage.getNick();

        if (arguments.isEmpty()) {
            engine.outService.enqueueMessageForSending(author, "Example: " + engine.prefix + "unban merc", isWhisper());
            log.info("Executed [unban] command by user: {} - target: not set", author);
            return;
        }

        if (arguments.stream().anyMatch("-all"::equals)) {
            engine.modService.unbanAll();
            log.info("Executed [unban all] command by user: {}", author);
            return;
        }
        arguments.stream()
                .findFirst()
                .ifPresent(target -> {
                    engine.modService.unban(target);
                    engine.outService.enqueueMessageForSending(author," unbanned " + target, isWhisper());

                    log.info("Executed [unban] command by user: {}, target: {}", author, target);
                });
    }
}
