package org.saturn.app.model.command.impl;

import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.listener.impl.MinerListenerImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"mine"})
public class MineTripCommandImpl extends UserCommandBaseImpl {
    private final ScheduledExecutorService executorService = newScheduledThreadPool(4);
    private final List<String> aliases = new ArrayList<>();
    private  List<Proxy> proxyList = new ArrayList<>();

    public MineTripCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getAdminTrips(engine));
        super.setAliases(this.getAliases());
        this.aliases.addAll(aliases);

        if (this.engine.proxies != null && this.engine.proxies.size() > 0) {
                    this.proxyList = this.engine.proxies.stream()
                            .map(proxy -> new Proxy(false, proxy.split(":")[0], proxy.split(":")[1]))
                            .collect(Collectors.toList());
        }
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

        if ("start".equals(cmd)) {
            long initialDelay = 40;
            String delay = arguments.get(2);
            if (delay == null || delay.isBlank()) {
                delay = String.valueOf(40);
            }

            List<Proxy> proxies = proxyList.stream().filter(p -> !p.isUsed())
                    .collect(Collectors.toList());

            if (!proxies.isEmpty()) {
                for (Proxy proxy : proxies) {
                    proxy.setUsed(true);
                    executorService.scheduleWithFixedDelay(() -> joinChannel(channel, proxy), initialDelay, Long.parseLong(delay), TimeUnit.SECONDS);
                    System.out.println("Started miner, initial delay: " + initialDelay + ", room: " + channel + ", delay: " + delay + ", proxy: " + proxy.getIp() + ":" + proxy.getPort());

                }
            } else {
                executorService.scheduleWithFixedDelay(() -> joinChannel(channel, null), initialDelay, Long.parseLong(delay), TimeUnit.SECONDS);
                System.out.println("Started miner, initial delay: " + initialDelay + ", room: " + channel + ", delay: " + delay);
            }


        } else if ("stop".equals(cmd)) {
            executorService.shutdownNow();
            System.out.println("Stopped mining, room: " + channel);
            try {
                System.out.println("Awaiting termination: " + executorService.isTerminated());
                boolean b = executorService.awaitTermination(10, TimeUnit.SECONDS);
                System.out.println("Miner service is terminated: " + b);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
    }

    private void joinChannel(String channel, Proxy proxyDto) {
        EngineImpl mineBot = new EngineImpl(null, null, false);

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

        mineBot.start(proxyDto);
    }
}
