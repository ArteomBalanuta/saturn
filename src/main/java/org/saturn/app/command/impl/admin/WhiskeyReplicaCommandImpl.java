package org.saturn.app.command.impl.admin;

import com.moandjiezana.toml.Toml;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.DataBaseServiceImpl;
import org.saturn.app.service.impl.OutService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

@Slf4j
@CommandAliases(aliases = {"whiskey"})
public class WhiskeyReplicaCommandImpl extends UserCommandBaseImpl {
    private final OutService outService;
    private static final Map<String, Proxy> PORT_MAPPED_BY_IP = new ConcurrentHashMap<>();
    private static final int REPLICA_STARTUP_WAIT_MS = 5000;
    private static final int RANDOM_NICK_LENGTH = 8;
    private static final AtomicInteger CHANNEL_COUNTER = new AtomicInteger(0);

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
                    .forEach(parts -> {
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
            log.info("Attempted to create duplicate replica for channel: {} by user: {}", targetChannel, author);
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

    private static String generateRandomNick() {
        return RandomStringUtils.random(RANDOM_NICK_LENGTH, true, true);
    }

    private static String generateTestChannel(String baseChannel, int proxyIndex) {
        return baseChannel + "_test_" + proxyIndex + "_" + System.currentTimeMillis();
    }

    private static EngineImpl createReplica(EngineImpl engine, String channel, String name) {
        Toml main = engine.getConfig();
        EngineImpl replica = new EngineImpl(
                new DataBaseServiceImpl(engine.dbPath).getConnection(),
                main,
                EngineType.AGENT
        );
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

    private void startReplicaWithProxyTesting(EngineImpl engine, String author, String targetChannel, String baseName)
            throws InterruptedException, ExecutionException {

        List<CompletableFuture<ProxyTestResult>> futures = new ArrayList<>();
        List<Proxy> proxies = new ArrayList<>(PORT_MAPPED_BY_IP.values());

        // Test each proxy with a DIFFERENT test channel
        for (int i = 0; i < proxies.size(); i++) {
            Proxy proxy = proxies.get(i);
            String testChannel = generateTestChannel(targetChannel, i);
            String testName = baseName + "_test_" + i;

            EngineImpl testReplica = createReplica(engine, testChannel, testName);

            int finalI = i;
            CompletableFuture<ProxyTestResult> future = CompletableFuture.supplyAsync(() ->
                    testProxyConnection(testReplica, proxy, testChannel, finalI)
            );
            futures.add(future);
        }

        // Find the first healthy proxy connection
        ProxyTestResult healthyResult = awaitFirstHealthyProxy(futures);

        if (healthyResult != null) {
            // Now connect to the ORIGINAL target channel using the healthy proxy
            connectToTargetChannel(engine, author, targetChannel, baseName, healthyResult);
        } else {
            sendErrorMessage(author, "Failed to establish any replica connection for channel: " + targetChannel);
            // Clean up any partially started test replicas
            futures.forEach(future -> future.cancel(true));
        }
    }

    private ProxyTestResult testProxyConnection(EngineImpl testReplica, Proxy proxy, String testChannel, int proxyIndex) {
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
            // Clean up test replica if it's not the one we'll use
            if (testReplica.isConnected()) {
                // Don't clean up yet - we might use this replica
                log.debug("Keeping test replica for potential reuse");
            } else {
                testReplica.stop();
            }
        }
    }

    private void connectToTargetChannel(EngineImpl engine, String author, String targetChannel,
                                        String baseName, ProxyTestResult healthyResult) {
        try {
            log.info("Using healthy proxy {} to connect to target channel: {}",
                    healthyResult.proxy.getIp(), targetChannel);

            // Create a NEW replica for the target channel using the healthy proxy
            EngineImpl targetReplica = createReplica(engine, targetChannel, baseName);
            targetReplica.start(healthyResult.proxy);
            Thread.sleep(REPLICA_STARTUP_WAIT_MS);

            if (targetReplica.isConnected()) {
                engine.addReplica(targetReplica);
                sendMessage(author, String.format(
                        "Started replica for channel '%s' successfully using proxy: %s",
                        targetChannel, healthyResult.proxy.getIp()
                ));

                // Clean up the test replica since we're using a new one
                if (healthyResult.testReplica != null && healthyResult.testReplica.isConnected()) {
                    healthyResult.testReplica.stop();
                    log.debug("Cleaned up test replica for channel: {}", healthyResult.testReplica.channel);
                }
            } else {
                sendErrorMessage(author, String.format(
                        "Healthy proxy %s failed to connect to target channel: %s",
                        healthyResult.proxy.getIp(), targetChannel
                ));

                // Fallback: try to use the test replica if it's still connected and reconfigure it
                if (healthyResult.testReplica != null && healthyResult.testReplica.isConnected()) {
                    log.info("Fallback: Reconfiguring test replica for target channel");
                    healthyResult.testReplica.setChannel(targetChannel);
                    engine.addReplica(healthyResult.testReplica);
                    sendMessage(author, String.format(
                            "Started replica for channel '%s' (fallback) using proxy: %s",
                            targetChannel, healthyResult.proxy.getIp()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to connect to target channel using proxy: {}", healthyResult.proxy.getIp(), e);
            sendErrorMessage(author, "Failed to connect to target channel: " + e.getMessage());
        }
    }

    private ProxyTestResult awaitFirstHealthyProxy(List<CompletableFuture<ProxyTestResult>> futures)
            throws InterruptedException, ExecutionException {

        while (!futures.isEmpty()) {
            CompletableFuture<ProxyTestResult> anyFuture = CompletableFuture.anyOf(
                    futures.toArray(new CompletableFuture[0])
            ).thenApply(result -> (ProxyTestResult) result);

            try {
                ProxyTestResult result = anyFuture.get();
                if (result != null && result.success) {
                    // Cancel all other test futures
                    futures.forEach(future -> future.cancel(true));
                    return result;
                } else {
                    // Remove failed future and continue waiting
                    futures.removeIf(future -> future.isDone());
                }
            } catch (ExecutionException e) {
                // Remove failed futures and continue
                futures.removeIf(CompletableFuture::isDone);
                if (futures.isEmpty()) {
                    throw e;
                }
            }
        }

        return null;
    }

    private void sendMessage(String recipient, String message) {
        outService.enqueueMessageForSending(recipient, message, chatMessage.isWhisper());
    }

    private void sendErrorMessage(String recipient, String message) {
        outService.enqueueMessageForSending(recipient, "Error: " + message, chatMessage.isWhisper());
    }

    // Helper class to store proxy test results
    private static class ProxyTestResult {
        final Proxy proxy;
        final EngineImpl testReplica;
        final boolean success;
        final int proxyIndex;

        ProxyTestResult(Proxy proxy, EngineImpl testReplica, boolean success, int proxyIndex) {
            this.proxy = proxy;
            this.testReplica = testReplica;
            this.success = success;
            this.proxyIndex = proxyIndex;
        }
    }
}
