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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"mine"})
public class MineTripCommandImpl extends UserCommandBaseImpl {
    private static final ScheduledExecutorService executorService = newScheduledThreadPool(1);
    private static boolean isMining = false;
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
        if (arguments.size() < 2) {
            super.engine.outService.enqueueMessageForSending("Example: " + engine.prefix + "mine <room> <start|stop>");
            return;
        }

        String channel = arguments.get(0);
        if (channel.equals(engine.channel)) {
            return;
        }

        String cmd = arguments.get(1);
        if (cmd == null || cmd.isBlank()) {
            return;
        }

        if ("start".equals(cmd) && !isMining) {
            isMining = true;
            executorService.scheduleWithFixedDelay(() -> joinChannel(channel), 5, 15, TimeUnit.SECONDS);
            System.out.println("Started mining, room: " + channel);
        } else if ("stop".equals(cmd)) {
            isMining = false;
            executorService.shutdown();
            System.out.println("Stopped mining, room: " + channel);
        }

    }

    private void joinChannel(String channel) {
        EngineImpl mineBot = new EngineImpl(null, null, false); // no db connection, nor config for this one is needed

        mineBot.isMain = false;
        mineBot.setChannel(channel);
        int nickLength = 8;
        boolean useLetters = true;
        boolean useNumbers = true;

        String nick = RandomStringUtils.random(nickLength, useLetters, useNumbers);
        String password = RandomStringUtils.random(32, useLetters, useNumbers);

        mineBot.setNick(nick);
        mineBot.setPassword(password);

        Listener listener = new MinerListenerImpl(mineBot);
        mineBot.setOnlineSetListener(listener);

        mineBot.start();
    }
}
