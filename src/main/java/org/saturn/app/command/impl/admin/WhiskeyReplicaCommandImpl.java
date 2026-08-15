package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import com.moandjiezana.toml.Toml;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.DataBaseServiceImpl;
import org.saturn.app.service.impl.OutService;

@Slf4j
@CommandAliases(aliases = {"whiskey"})
public class WhiskeyReplicaCommandImpl extends UserCommandBaseImpl {
  private final OutService outService;
  private static final Map<String, Proxy> PORT_MAPPED_BY_IP = new ConcurrentHashMap<>();
  private static final int REPLICA_STARTUP_WAIT_MS = 5000;

  public WhiskeyReplicaCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
    this.outService = super.engine.outService;
    initializeProxyMapping();
  }

  private void initializeProxyMapping() {
    if (engine.proxies != null && !engine.proxies.isEmpty()) {
      engine.proxies.stream()
          .map(proxy -> proxy.split(":"))
          .filter(parts -> parts.length == 2)
          .forEach(
              parts -> {
                Proxy proxy = new Proxy(false, parts[0], parts[1]);
                PORT_MAPPED_BY_IP.put(proxy.getIp(), proxy);
              });
    }
  }

  @Override
  public Optional<Status> execute() {
    List<String> arguments = getArguments();

    if (arguments.size() < 2) {
      sendErrorMessage(chatMessage.getNick(), "Usage: whiskey <channel> <name>");
      return Optional.of(Status.FAILED);
    }

    String targetChannel = arguments.get(0).trim();
    String name = arguments.get(1);
    String author = chatMessage.getNick();

    // Check if replica already exists for target channel
    if (engine.replicasMappedByChannel.containsKey(targetChannel)) {
      sendMessage(author, "Replica already exists for channel: " + targetChannel);
      log.info(
          "Attempted to create duplicate replica for channel: {} by user: {}",
          targetChannel,
          author);
      return Optional.of(Status.SUCCESSFUL);
    }

    log.debug("Registering replica for target channel: {}", targetChannel);

    try {
      String replicaName = (name == null || name.trim().isEmpty()) ? "portal" : name.trim();
      registerReplica(engine, author, targetChannel, replicaName);
      log.info("Successfully started replica for channel: {} by user: {}", targetChannel, author);
      sendMessage(author, "Successfully started replica for channel: " + targetChannel);
    } catch (Exception e) {
      log.error("Failed to start replica for channel: {} by user: {}", targetChannel, author, e);
      sendErrorMessage(author, "Failed to start replica: " + e.getMessage());
      return Optional.of(Status.FAILED);
    }

    return Optional.of(Status.SUCCESSFUL);
  }

  private static EngineImpl createReplica(EngineImpl engine, String channel, String name) {
    Toml main = engine.getConfig();
    EngineImpl replica =
        new EngineImpl(
            new DataBaseServiceImpl(engine.dbPath).getConnection(), main, EngineType.AGENT);
    replica.setChannel(channel);
    replica.setNick(name);
    return replica;
  }

  public void registerReplica(EngineImpl engine, String author, String targetChannel, String name)
      throws InterruptedException, ExecutionException {

    // Fast path: no proxies configured
    if (PORT_MAPPED_BY_IP.isEmpty()) {
      startReplicaDirectly(engine, author, targetChannel, name);
      return;
    }

    // Proxy path: test each proxy with a different test channel
    startReplicaWithProxyTesting(engine, author, targetChannel, name);
  }

  private void startReplicaDirectly(EngineImpl engine, String author, String channel, String name) {
    EngineImpl replica = createReplica(engine, channel, name);
    replica.start();
    engine.addReplica(replica);
    sendMessage(author, "Started replica for channel: " + channel);
  }

  private void startReplicaWithProxyTesting(
      EngineImpl engine, String author, String targetChannel, String baseName)
      throws InterruptedException, ExecutionException {

    List<CompletableFuture<ProxyTestResult>> futures = new ArrayList<>();
    List<Proxy> proxies = new ArrayList<>(PORT_MAPPED_BY_IP.values());

    for (int i = 0; i < proxies.size(); i++) {
      Proxy proxy = proxies.get(i);
      String testChannel = targetChannel + "_test_" + i + "_" + System.currentTimeMillis();
      String testName = baseName + "_test_" + i;

      EngineImpl testReplica = createReplica(engine, testChannel, testName);

      int finalI = i;
      CompletableFuture<ProxyTestResult> future =
          CompletableFuture.supplyAsync(
              () -> testProxyConnection(testReplica, proxy, testChannel, finalI));
      futures.add(future);
    }

    List<ProxyTestResult> healthyProxies = awaitHealthyProxies(futures);

    if (healthyProxies.isEmpty()) {
      sendErrorMessage(
          author, "Failed to establish any replica connection for channel: " + targetChannel);
      return;
    }

    // Use the first healthy proxy for the target channel
    ProxyTestResult primaryResult = healthyProxies.get(0);
    connectToTargetChannel(
        engine,
        author,
        targetChannel,
        baseName,
        primaryResult,
        healthyProxies.subList(1, healthyProxies.size()));
  }

  private ProxyTestResult testProxyConnection(
      EngineImpl testReplica, Proxy proxy, String testChannel, int proxyIndex) {
    try {
      log.debug("Testing proxy {} with test channel: {}", proxy.getIp(), testChannel);
      testReplica.start(proxy);
      Thread.sleep(REPLICA_STARTUP_WAIT_MS);

      if (testReplica.isConnected()) {
        log.info("Proxy {} is healthy (test channel: {})", proxy.getIp(), testChannel);
        return new ProxyTestResult(proxy, testReplica, true, proxyIndex);
      } else {
        log.warn("Proxy {} failed to connect (test channel: {})", proxy.getIp(), testChannel);
        return new ProxyTestResult(proxy, null, false, proxyIndex);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while testing proxy: {}", proxy.getIp());
      return new ProxyTestResult(proxy, null, false, proxyIndex);
    } catch (Exception e) {
      log.error("Exception while testing proxy: {}", proxy.getIp(), e);
      return new ProxyTestResult(proxy, null, false, proxyIndex);
    } finally {
      if (testReplica.isConnected()) {
        log.debug("Keeping test replica for potential reuse");
      } else {
        testReplica.stop();
      }
    }
  }

  private void connectToTargetChannel(
      EngineImpl engine,
      String author,
      String targetChannel,
      String baseName,
      ProxyTestResult healthyResult,
      List<ProxyTestResult> backupProxies) {
    try {
      log.info(
          "Using healthy proxy {} to connect to target channel: {}",
          healthyResult.proxy.getIp(),
          targetChannel);

      EngineImpl targetReplica = createReplica(engine, targetChannel, baseName);
      targetReplica.start(healthyResult.proxy);
      Thread.sleep(REPLICA_STARTUP_WAIT_MS);

      if (targetReplica.isConnected()) {
        engine.addReplica(targetReplica);
        sendMessage(
            author,
            String.format(
                "Started replica for channel '%s' successfully using proxy: %s",
                targetChannel, healthyResult.proxy.getIp()));

        // Store backup proxies for later use when primary disconnects
        if (!backupProxies.isEmpty()) {
          engine.backupProxiesByChannel.put(targetChannel, backupProxies);
          log.info("Stored {} backup proxies for channel {}", backupProxies.size(), targetChannel);
        }

        // Keep the primary test replica as a backup instead of stopping it
        if (healthyResult.testReplica != null && healthyResult.testReplica.isConnected()) {
          log.info(
              "Keeping test replica {} as backup for channel {}",
              healthyResult.testReplica.channel,
              targetChannel);
        }
      } else {
        sendErrorMessage(
            author,
            String.format(
                "Healthy proxy %s failed to connect to target channel: %s",
                healthyResult.proxy.getIp(), targetChannel));

        if (healthyResult.testReplica != null && healthyResult.testReplica.isConnected()) {
          log.info("Fallback: Reconfiguring test replica for target channel");
          healthyResult.testReplica.setChannel(targetChannel);
          engine.addReplica(healthyResult.testReplica);
          sendMessage(
              author,
              String.format(
                  "Started replica for channel '%s' (fallback) using proxy: %s",
                  targetChannel, healthyResult.proxy.getIp()));
        }
      }
    } catch (Exception e) {
      log.error(
          "Failed to connect to target channel using proxy: {}", healthyResult.proxy.getIp(), e);
      sendErrorMessage(author, "Failed to connect to target channel: " + e.getMessage());
    }
  }

  private List<ProxyTestResult> awaitHealthyProxies(
      List<CompletableFuture<ProxyTestResult>> futures)
      throws InterruptedException, ExecutionException {

    List<ProxyTestResult> healthyProxies = new ArrayList<>();
    for (CompletableFuture<ProxyTestResult> future : futures) {
      ProxyTestResult result = future.get();
      if (result != null && result.success) {
        healthyProxies.add(result);
      } else {
        // Cancel failed futures
        future.cancel(true);
      }
    }

    return healthyProxies;
  }

  public static CompletableFuture<Void> reconnectWithBackupProxy(
      EngineImpl engine, String author, String targetChannel, String baseName) {
    return CompletableFuture.runAsync(
        () -> reconnectWithBackupProxyInternal(engine, author, targetChannel, baseName));
  }

  private static void reconnectWithBackupProxyInternal(
      EngineImpl engine, String author, String targetChannel, String baseName) {
    List<ProxyTestResult> backupProxies = engine.backupProxiesByChannel.get(targetChannel);
    if (backupProxies == null || backupProxies.isEmpty()) {
      log.warn("No backup proxies available for channel {}", targetChannel);
      return;
    }

    ProxyTestResult backupResult = backupProxies.remove(0);
    engine.backupProxiesByChannel.put(targetChannel, backupProxies);

    try {
      log.info(
          "Reconnecting to channel {} using backup proxy {}",
          targetChannel,
          backupResult.proxy.getIp());
      EngineImpl targetReplica = createReplica(engine, targetChannel, baseName);
      targetReplica.start(backupResult.proxy);
      Thread.sleep(REPLICA_STARTUP_WAIT_MS);

      if (targetReplica.isConnected()) {
        engine.addReplica(targetReplica);
        log.info(
            "Successfully reconnected to channel {} via backup proxy {}",
            targetChannel,
            backupResult.proxy.getIp());
      } else {
        log.warn(
            "Backup proxy {} failed to connect to channel {}",
            backupResult.proxy.getIp(),
            targetChannel);
        targetReplica.stop();
        reconnectWithBackupProxyInternal(engine, author, targetChannel, baseName);
      }
    } catch (Exception e) {
      log.error("Failed to reconnect using backup proxy: {}", backupResult.proxy.getIp(), e);
      reconnectWithBackupProxyInternal(engine, author, targetChannel, baseName);
    }
  }

  private void sendMessage(String recipient, String message) {
    outService.enqueueMessageForSending(recipient, message, chatMessage.isWhisper());
  }

  private void sendErrorMessage(String recipient, String message) {
    outService.enqueueMessageForSending(recipient, "Error: " + message, chatMessage.isWhisper());
  }

  public static class ProxyTestResult {
    final Proxy proxy;
    final EngineImpl testReplica;
    final boolean success;
    final int proxyIndex;

    public ProxyTestResult(Proxy proxy, EngineImpl testReplica, boolean success, int proxyIndex) {
      this.proxy = proxy;
      this.testReplica = testReplica;
      this.success = success;
      this.proxyIndex = proxyIndex;
    }
  }
}
