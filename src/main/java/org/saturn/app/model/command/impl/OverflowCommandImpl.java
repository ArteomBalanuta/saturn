package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminTrips;

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
    public void execute() {
        List<String> arguments = getArguments();

        if (arguments.isEmpty()) {
            super.engine.outService.enqueueMessageForSending(chatMessage.getNick(), " victim's nick isn't set! Example: "+ engine.prefix + "shoot @_", isWhisper());
            return;
        }

        Optional<String> argument = arguments.stream().findFirst();
        String target = argument.get().replace("@", "");
        engine.modService.overflow(target);
    }
}
