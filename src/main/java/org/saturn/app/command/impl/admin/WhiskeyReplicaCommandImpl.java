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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

@Slf4j
@CommandAliases(aliases = {"whiskey"})
public class WhiskeyReplicaCommandImpl extends UserCommandBaseImpl {
    private final OutService outService;
    private static final HashMap<String, Proxy> portMappedByIp = new HashMap<>();

    public WhiskeyReplicaCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getAdminAndUserTrips(engine));
        super.setAliases(aliases);
        this.outService = super.engine.outService;

        if (this.engine.proxies != null && !this.engine.proxies.isEmpty()) {
            this.engine.proxies.stream()
                    .map(proxy -> new Proxy(false, proxy.split(":")[0], proxy.split(":")[1]))
                    .forEach(p -> portMappedByIp.put(p.getIp(), p));
        }
    }

    @Override
    public Optional<Status> execute() {
        String author = super.chatMessage.getNick();

        List<String> arguments = this.getArguments();

        String channel = arguments.getFirst().trim();
        String name = arguments.get(1);
        if (engine.replicasMappedByChannel.get(channel) == null) {
            log.debug("Registering replica for channel: {}", channel);
            try {
                registerReplica(engine, chatMessage, author, channel, name == null ? "portal" : name);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log.info("Successfully started replica for channel: {}", channel);
        }

        log.info("Executed [whiskey] command by user: {}, channel: {}", author, channel);
        return Optional.of(Status.SUCCESSFUL);
    }

    private static final String generateRandomNick(int length, boolean useLetters, boolean useNumbers) {
        return RandomStringUtils.random(length, useLetters, useNumbers);
    }

    private static EngineImpl createAndStartReplica(
            EngineImpl engine, 
            ChatMessage chatMessage,
            String author,
            String channel,
            String name) {
        Toml main = engine.getConfig();
        EngineImpl replica = new EngineImpl(
                new DataBaseServiceImpl(engine.dbPath).getConnection(),
                main,
                EngineType.AGENT
        );
        replica.setChannel(channel);
        replica.setNick(name == null ? generateRandomNick(8, true, true) : name);
        return replica;
    }

    public static void registerReplica(
            EngineImpl engine, 
            ChatMessage chatMessage,
            String author,
            String channel,
            String name) throws InterruptedException, ExecutionException {
        
        if (portMappedByIp.isEmpty()) {
            EngineImpl replica = createAndStartReplica(engine, chatMessage, author, channel, name);
            replica.start();
            engine.outService.enqueueMessageForSending(
                    author,
                    "started replica at whiskey channel: " + channel + " successfully. Number of replicas: " + engine.replicasMappedByChannel.size(),
                    chatMessage.isWhisper());
            return;
        }

        List<CompletableFuture<EngineImpl>> futures = new ArrayList<>();
        List<EngineImpl> replicas = new ArrayList<>();

        int n = 0;
        for (Map.Entry<String, Proxy> ipAndProxy : portMappedByIp.entrySet()) {
            EngineImpl replica = createAndStartReplica(engine, chatMessage, author, channel, name + n++);
            replicas.add(replica);

            var proxy = ipAndProxy.getValue();
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    replica.start(proxy);
                    Thread.sleep(5000);
                    return replica;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }));
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allFutures.get();

        // Process results
        EngineImpl successfulReplica = null;
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<EngineImpl> future = futures.get(i);
            EngineImpl replica = replicas.get(i);

            future.whenComplete((result, error) -> {
                if (error != null || result == null) {
                    log.error("Failed to connect replica using proxy: " + portMappedByIp.get(i).getValue().getIp());
                    return;
                }

                if (result.isConnected()) {
                    if (successfulReplica == null) {
                        successfulReplica = result;
                        engine.addReplica(result);
                        engine.outService.enqueueMessageForSending(
                                author,
                                "started replica at whiskey channel using proxy: " + portMappedByIp.get(i).getValue().getIp() + " successfully",
                                chatMessage.isWhisper());
                    } else {
                        // Close unused replicas
                        result.close();
                    }
                }
            });
        }

        if (successfulReplica == null) {
            engine.outService.enqueueMessageForSending(
                    author,
                    "Failed to establish any replica connection for channel: " + channel,
                    chatMessage.isWhisper());
        } else {
            engine.outService.enqueueMessageForSending(
                    author,
                    "Successfully connected " + engine.replicasMappedByChannel.size() + " replicas for channel: " + channel,
                    chatMessage.isWhisper());
        }
    }
}
