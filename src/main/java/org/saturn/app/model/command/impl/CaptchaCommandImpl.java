package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"captcha"})
public class CaptchaCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public CaptchaCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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

        if ("on".equals(argument.get())) {
            engine.modService.enableCaptcha();
            super.engine.outService.enqueueMessageForSending("Captcha enabled!");
        } else if ("off".equals(argument.get())) {
            engine.modService.disableCaptcha();
            super.engine.outService.enqueueMessageForSending("Captcha disabled!");
        }
    }
}
