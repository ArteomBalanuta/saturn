package org.saturn.app.model.command.impl;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.listener.impl.MinerListenerImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"mine"})
public class MineTripCommandImpl extends UserCommandBaseImpl {
    private static final ScheduledThreadPoolExecutor executorService = new ScheduledThreadPoolExecutor(32);
    private static final List<Future<?>> tasks = new ArrayList<>();
    private final List<String> aliases = new ArrayList<>();

    private static final HashMap<String, Proxy> portMappedByIp = new HashMap<>();

    private static final ScheduledThreadPoolExecutor executorServiceTaskChecker = new ScheduledThreadPoolExecutor(1);

    public MineTripCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getAdminTrips(engine));
        super.setAliases(this.getAliases());
        this.aliases.addAll(aliases);

        if (this.engine.proxies != null && !this.engine.proxies.isEmpty()) {
            this.engine.proxies.stream()
                    .map(proxy -> new Proxy(false, proxy.split(":")[0], proxy.split(":")[1]))
                    .forEach(p -> portMappedByIp.put(p.getIp(), p));
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

        if ("count".equals(cmd)) {
            int activeCount = executorService.getActiveCount();
            long completedTaskCount = executorService.getCompletedTaskCount();
            long taskCount = executorService.getTaskCount();
            super.engine.outService.enqueueMessageForSending("TaskCount: " + taskCount + ", Completed: " + completedTaskCount + ", Active: " + activeCount);
            return;
        }

        if ("start".equals(cmd)) {
            long initialDelay = 5;
            String delay = arguments.get(2);
            if (delay == null || delay.isBlank()) {
                delay = String.valueOf(35);
            }

            if (!portMappedByIp.isEmpty()) {
                for (Map.Entry<String, Proxy> ipnProxy : portMappedByIp.entrySet()) {
                    executorService.scheduleWithFixedDelay(() -> joinChannel(channel, ipnProxy.getValue()), initialDelay, Long.parseLong(delay), TimeUnit.SECONDS);
                    System.out.println("Started miner, initial delay: " + initialDelay + ", room: " + channel + ", delay: " + delay + ", proxy: " + ipnProxy.getValue().getIp() + ":" + ipnProxy.getValue().getPort());
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

        executorServiceTaskChecker.scheduleWithFixedDelay(MineTripCommandImpl::check, 1, 5, TimeUnit.SECONDS);
    }

    private static void check() {
        try {
            for (Future<?> task : tasks) {
                if (task.isDone()) {
                    Object o = task.get();
                    System.out.println("mined successfully: " + o);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            System.out.println("Caught: " + e);
        }
    }

    private void joinChannel(String channel, Proxy proxyDto) {
        Configuration config = super.engine.getConfig();
        EngineImpl mineBot = new EngineImpl(null, config, false);

        mineBot.isMain = false;
        mineBot.setChannel(channel);
        int nickLength = 8;
        boolean useLetters = true;
        boolean useNumbers = true;

        String nick = RandomStringUtils.random(nickLength, useLetters, useNumbers);
        String password = RandomStringUtils.random(128, useLetters, useNumbers);

        mineBot.setNick(nick);
        mineBot.setPassword(password);

        Listener listener = new MinerListenerImpl(mineBot);
        mineBot.setOnlineSetListener(listener);

        try {
            mineBot.start(proxyDto);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
