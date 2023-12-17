package org.saturn.app.model.command.impl;

import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.listener.impl.MinerListenerImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;

import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"mine"})
public class MineTripCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public MineTripCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
        List<String> arguments = this.getArguments();
        if (arguments.size() == 0) {
            super.engine.outService.enqueueMessageForSending("Example: " + engine.prefix + "mine lab");
            return;
        }

        String channel = arguments.get(0);
        if (channel.equals(engine.channel)) {
            return;
        }

        /* Use `ScheduledExecutorService` */
        joinChannel(channel);
        try {
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    private void joinChannel(String channel) {
        EngineImpl mineBot = new EngineImpl(null, null, false); // no db connection, nor config for this one is needed

        mineBot.isMain = false;
        mineBot.setChannel(channel);
        int length = 8;
        boolean useLetters = true;
        boolean useNumbers = true;
        String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);

        mineBot.setNick(generatedNick);

        mineBot.setPassword(generatedNick);

        Listener listener = new MinerListenerImpl(mineBot);
        mineBot.setOnlineSetListener(listener);

        mineBot.start();
    }
}
