package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminTrips;

@Slf4j
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

        String author = chatMessage.getNick();
        if (arguments.isEmpty()) {
            engine.modService.lock();
            log.info("Executed [captcha] command by user: {} - captcha: enabled", author);
            super.engine.outService.enqueueMessageForSending(author," Captcha enabled!", isWhisper());
            return;
        }

        Optional<String> argument = arguments.stream().findFirst();
        if ("on".equals(argument.get())) {
            engine.modService.enableCaptcha();
            super.engine.outService.enqueueMessageForSending(author," Captcha enabled!", isWhisper());
            log.info("Executed [captcha] command by user: {}, captcha: enabled", author);
        } else if ("off".equals(argument.get())) {
            engine.modService.disableCaptcha();
            super.engine.outService.enqueueMessageForSending(author, " Captcha disabled!", isWhisper());
            log.info("Executed [captcha] command by user: {}, captcha: disabled", author);
        }
    }
}
