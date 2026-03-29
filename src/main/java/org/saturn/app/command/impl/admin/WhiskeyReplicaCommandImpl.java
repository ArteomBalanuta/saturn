package org.saturn.app.command.impl.admin;

import com.moandjiezana.toml.Toml;
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
            registerReplica(engine, chatMessage, author, channel, name == null ? "portal" : name);
            log.info("Successfully started replica for channel: {}", channel);
        }

        log.info("Executed [whiskey] command by user: {}, channel: {}", author, channel);
        return Optional.of(Status.SUCCESSFUL);
    }

    public static void registerReplica(
            EngineImpl engine, ChatMessage chatMessage, String author, String channel, String name) {
        Toml main = engine.getConfig();
        EngineImpl replica =
                new EngineImpl(new DataBaseServiceImpl(engine.dbPath).getConnection(), main, EngineType.AGENT);
        replica.setChannel(channel);
        replica.setNick(name);

        /* register replica */
        engine.addReplica(replica);

        if (!portMappedByIp.isEmpty()) {
            for (Map.Entry<String, Proxy> ipAndProxy : portMappedByIp.entrySet()) {
                var proxy = ipAndProxy.getValue();
                replica.start(proxy);
                engine.outService.enqueueMessageForSending(
                        author,
                        "started replica at whiskey channel using SOCKS5 proxy: "
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
