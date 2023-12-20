package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"overflow", "shoot", "sex", "gay"})
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
            engine.modService.lock();
        }

        Optional<String> argument = arguments.stream().findFirst();

        if (argument.isEmpty()) {
            super.engine.outService.enqueueMessageForSending("Victim nick isn't set! Example: shoot @_");
        }

        String target = argument.get().replace("@", "");

        engine.modService.overflow(target);

    }
}
