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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public static void registerReplica(
            EngineImpl engine, ChatMessage chatMessage, String author, String channel, String name) throws InterruptedException {
        Toml main = engine.getConfig();
        EngineImpl replica =
                new EngineImpl(new DataBaseServiceImpl(engine.dbPath).getConnection(), main, EngineType.AGENT);
        replica.setChannel(channel);
        replica.setNick(name);

        if (!portMappedByIp.isEmpty()) {
            if (portMappedByIp.size() == 1) {
                for (Map.Entry<String, Proxy> ipAndProxy : portMappedByIp.entrySet()) {
                    var proxy = ipAndProxy.getValue();
                    replica.start(proxy);
                    Thread.sleep(5_000);
                    if (replica.isConnected()) {
                        engine.outService.enqueueMessageForSending(
                                author,
                                "started replica at whiskey channel using SOCKS5 proxy: "
                                        + channel
                                        + " successfully. Number of replicas: "
                                        + engine.replicasMappedByChannel.size(),
                                chatMessage.isWhisper());
                    } else {
                        engine.outService.enqueueMessageForSending(
                                author,
                                "couldn't connect replica to whiskey channel using SOCKS5 proxy: "
                                        + channel,
                                chatMessage.isWhisper());
                    }
                }
            } else {
                for (Map.Entry<String, Proxy> ipAndProxy : portMappedByIp.entrySet()) {
                    EngineImpl proxyReplica =
                            new EngineImpl(new DataBaseServiceImpl(engine.dbPath).getConnection(), main, EngineType.AGENT);
                    proxyReplica.setChannel(channel);

                    int length = 8;
                    boolean useLetters = true;
                    boolean useNumbers = true;
                    String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);

                    proxyReplica.setNick(generatedNick);

                    var proxy = ipAndProxy.getValue();

                    proxyReplica.setNick(generatedNick);
                    proxyReplica.start(proxy);
                    Thread.sleep(5_000);
                    if (proxyReplica.isConnected()) {
                        engine.outService.enqueueMessageForSending(
                                author,
                                "started replica at whiskey channel using SOCKS5 proxy: "
                                        + channel
                                        + " successfully. Number of replicas: "
                                        + engine.replicasMappedByChannel.size(),
                                chatMessage.isWhisper());

                        /* register replica */
                        engine.addReplica(proxyReplica);
                        return;
                    }
                }

                engine.outService.enqueueMessageForSending(
                        author,
                        "started replicas at whiskey channel using " + portMappedByIp.size() + " SOCKS5 proxies: "
                                + channel
                                + " successfully. Number of replicas: "
                                + engine.replicasMappedByChannel.size(),
                        chatMessage.isWhisper());
            }
        } else {
            replica.start();
            engine.outService.enqueueMessageForSending(
                    author,
                    "started replica at whiskey channel: "
                            + channel
                            + " successfully. Number of replicas: "
                            + engine.replicasMappedByChannel.size(),
                    chatMessage.isWhisper());
        }
    }
}
