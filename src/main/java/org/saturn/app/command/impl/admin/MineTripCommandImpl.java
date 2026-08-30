package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getAdminTrips;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.snapshot.DefaultRoomSnapshotCoordinator;
import org.saturn.app.listener.snapshot.EngineSnapshotSession;
import org.saturn.app.listener.snapshot.GsonOnlineSetPayloadParser;
import org.saturn.app.listener.snapshot.MineTripOperation;
import org.saturn.app.listener.snapshot.RoomSnapshotRequest;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.payload.ChatMessage;

// vim clear proxy value
// %s/proxies= "\zs.*\ze"/
//  awk '$4 ~ /^[0-9]+$/ {print $4}' trips.txt | sort
//  awk '$4 ~ /^[a-z]+$/ {print $4}' trips.txt | sort

/* TODO: implement logging separately too not flood the main logging output. P.S This class is horrible. */
@Slf4j
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
      log.info(
          "Started miner, initial delay: %d, room: %s, delay: %d, proxy: %s:%s"
              .formatted(
                  initialDelaySeconds, channel, delaySeconds, proxy.getIp(), proxy.getPort()));
      return;
    }

    log.info(
        "Started miner, initial delay: %d, room: %s, delay: %d"
            .formatted(initialDelaySeconds, channel, delaySeconds));
  }

  private void stopMining(String channel) {
    executorService.shutdownNow();
    log.info("Stopped mining, room: %s".formatted(channel));
    try {
      log.info("Awaiting termination: %s".formatted(executorService.isTerminated()));
      boolean terminated = executorService.awaitTermination(10, TimeUnit.SECONDS);
      log.info("Miner service is terminated: %s".formatted(terminated));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private static void check() {
    try {
      for (Future<?> task : tasks) {
        if (task.isDone()) {
          Object o = task.get();
          log.info("mined successfully: " + o);
        }
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      log.info("Caught: " + e);
    }
  }

  private void joinChannel(String channel, Proxy proxyDto) {
    String workflowId = UUID.randomUUID().toString();
    String nick = RandomStringUtils.random(8, true, true);
    String password = RandomStringUtils.random(128, true, true);
    DefaultRoomSnapshotCoordinator coordinator =
        new DefaultRoomSnapshotCoordinator(
            (request, sink) ->
                EngineSnapshotSession.create(
                    workflowId, engine.getConfig(), channel, nick, password, sink),
            (request, reply) -> log.warn("Mining workflow {}: {}", workflowId, reply),
            new GsonOnlineSetPayloadParser(EngineType.LIST_CMD, workflowId, channel));
    coordinator.submit(
        new RoomSnapshotRequest(
            workflowId,
            nick,
            engine.channel,
            channel,
            null,
            null,
            new MineTripOperation(nick, password, java.nio.file.Paths.get("trips.txt"))));
  }
}
