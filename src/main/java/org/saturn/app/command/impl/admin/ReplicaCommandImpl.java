package org.saturn.app.command.impl.admin;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.OutService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@Slf4j
@CommandAliases(aliases = {"replica", "bot", "agent"})
public class ReplicaCommandImpl extends UserCommandBaseImpl {
    private final OutService outService;
    private final List<String> aliases = new ArrayList<>();
    public ReplicaCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getWhiteListedTrips(engine));
        super.setAliases(this.getAliases());
        this.outService = super.engine.outService;
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
    public Role getAuthorizedRole() {
        return Role.ADMIN;
    }
    @Override
    public Optional<Status> execute() {
        String author = super.chatMessage.getNick();

        List<String> arguments = this.getArguments();
        if (arguments.isEmpty()) {
            outService.enqueueMessageForSending(author, "Example: " + engine.prefix + "replica lounge", isWhisper());
            return Optional.of(Status.FAILED);
        }

        String channel = arguments.get(0).trim();
        if (channel.isBlank() || channel.equals(engine.channel)) {
            outService.enqueueMessageForSending(author, "I'm the host bot serving current channel. Example: " + engine.prefix + "replica lounge", isWhisper());
        } else {
            if (engine.replicasMappedByChannel.get(channel) == null) {
                log.debug("Registering replica for channel: {}", channel);
                registerChannel(author, channel);
                log.info("Successfully started replica for channel: {}", channel);
            } else {
                log.warn("Channel: {} already has a replica running", channel);
            }
        }

        log.info("Executed [replica] command by user: {}, channel: {}", author, channel);
        return Optional.of(Status.SUCCESSFUL);
    }

    public void registerChannel(String author, String channel) {
        Configuration main = engine.getConfig();
        EngineImpl replica = new EngineImpl(engine.getDbConnection(), main, EngineType.REPLICA);
        replica.setChannel(channel);
        replica.setNick(engine.nick.concat("Replica"));
        replica.setPassword(engine.password);

        /* register replica */
        engine.addReplica(replica);

        replica.start();

        engine.outService.enqueueMessageForSending(author, "started replica in channel: " + channel + " successfully. Number of agents: " + engine.replicasMappedByChannel.size(), chatMessage.isWhisper());
    }
}
