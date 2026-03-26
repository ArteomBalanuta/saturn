package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getAdminTrips;

import com.moandjiezana.toml.Toml;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.listener.impl.MinerListenerImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.payload.ChatMessage;

// vim clear proxy value
// %s/proxies= "\zs.*\ze"/
//  awk '$4 ~ /^[0-9]+$/ {print $4}' trips.txt | sort
//  awk '$4 ~ /^[a-z]+$/ {print $4}' trips.txt | sort

/* TODO: implement logging separately too not flood the main logging output. P.S This class is horrible. */
@CommandAliases(aliases = {"mine"})
public class MineTripCommandImpl extends UserCommandBaseImpl {
  private static final ScheduledThreadPoolExecutor executorService =
      new ScheduledThreadPoolExecutor(32);
  private static final List<Future<?>> tasks = new ArrayList<>();

  private static final HashMap<String, Proxy> portMappedByIp = new HashMap<>();

  private static final ScheduledThreadPoolExecutor executorServiceTaskChecker =
      new ScheduledThreadPoolExecutor(1);

  public MineTripCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);

    if (this.engine.proxies != null && !this.engine.proxies.isEmpty()) {
      this.engine.proxies.stream()
          .map(proxy -> new Proxy(false, proxy.split(":")[0], proxy.split(":")[1]))
          .forEach(p -> portMappedByIp.put(p.getIp(), p));
    }
  }

  @Override
  public Optional<Status> execute() {
    List<String> arguments = this.getArguments();
    if (arguments.size() < 2) {
      return fail(" Example: %smine <room> <start|stop>".formatted(engine.prefix));
    }

    String channel = arguments.get(0);
    if (channel.equals(engine.channel)) {
      return Optional.of(Status.FAILED);
    }

    String cmd = arguments.get(1);
    if (cmd == null || cmd.isBlank()) {
      return Optional.of(Status.FAILED);
    }

    Optional<Status> result = handleCommand(arguments, channel, cmd);
    if (result.isPresent()) {
      return result;
    }

    executorServiceTaskChecker.scheduleWithFixedDelay(
        MineTripCommandImpl::check, 1, 5, TimeUnit.SECONDS);
    return successful();
  }

  private Optional<Status> handleCommand(List<String> arguments, String channel, String command) {
    if ("count".equals(command)) {
      printTaskCount();
      return successful();
    }
    if ("start".equals(command)) {
      startMining(channel, parseDelay(arguments));
      return Optional.empty();
    }
    if ("stop".equals(command)) {
      stopMining(channel);
      return successful();
    }
    return Optional.of(Status.FAILED);
  }

  private void printTaskCount() {
    int activeCount = executorService.getActiveCount();
    long completedTaskCount = executorService.getCompletedTaskCount();
    long taskCount = executorService.getTaskCount();
    replyToAuthor(
        " TaskCount: %d, Completed: %d, Active: %d"
            .formatted(taskCount, completedTaskCount, activeCount));
  }

  private long parseDelay(List<String> arguments) {
    if (arguments.size() < 3 || arguments.get(2) == null || arguments.get(2).isBlank()) {
      return 35L;
    }
    return Long.parseLong(arguments.get(2));
  }

  private void startMining(String channel, long delaySeconds) {
    long initialDelay = 5L;
    if (!portMappedByIp.isEmpty()) {
      for (Map.Entry<String, Proxy> ipAndProxy : portMappedByIp.entrySet()) {
        scheduleMiningTask(channel, ipAndProxy.getValue(), initialDelay, delaySeconds);
      }
      return;
    }
    scheduleMiningTask(channel, null, initialDelay, delaySeconds);
  }

  private void scheduleMiningTask(
      String channel, Proxy proxy, long initialDelaySeconds, long delaySeconds) {
    executorService.scheduleWithFixedDelay(
        () -> joinChannel(channel, proxy), initialDelaySeconds, delaySeconds, TimeUnit.SECONDS);

    if (proxy != null) {
      System.out.println(
          "Started miner, initial delay: %d, room: %s, delay: %d, proxy: %s:%s"
              .formatted(
                  initialDelaySeconds, channel, delaySeconds, proxy.getIp(), proxy.getPort()));
      return;
    }

    System.out.println(
        "Started miner, initial delay: %d, room: %s, delay: %d"
            .formatted(initialDelaySeconds, channel, delaySeconds));
  }

  private void stopMining(String channel) {
    executorService.shutdownNow();
    System.out.println("Stopped mining, room: %s".formatted(channel));
    try {
      System.out.println("Awaiting termination: %s".formatted(executorService.isTerminated()));
      boolean terminated = executorService.awaitTermination(10, TimeUnit.SECONDS);
      System.out.println("Miner service is terminated: %s".formatted(terminated));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
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
    Toml config = super.engine.getConfig();
    EngineImpl mineBot = new EngineImpl(null, config, EngineType.LIST_CMD);

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
