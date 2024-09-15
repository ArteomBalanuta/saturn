package org.saturn.app.command.impl.admin;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.OutService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@Slf4j
@CommandAliases(aliases = {"replicaoff","offline","botoff","agentoff"})
public class ReplicaOffCommandImpl extends UserCommandBaseImpl {
    private final OutService outService;
    private final List<String> aliases = new ArrayList<>();
    public ReplicaOffCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
            outService.enqueueMessageForSending(author, "Example: " + engine.prefix + "replicaoff lounge", isWhisper());
            log.info("Executed [replicaoff] command by user: {}, no channel set", author);
            return Optional.of(Status.FAILED);
        }

        String channel = arguments.get(0).trim();
        if (channel.isBlank() || channel.equals(engine.channel)) {
            outService.enqueueMessageForSending(author, "I'm the host bot serving current channel, not a replica.", isWhisper());
        } else {
            EngineImpl replica = engine.replicasMappedByChannel.get(channel);
            if (replica == null) {
                log.warn("No replica in channel: {}", channel);
                outService.enqueueMessageForSending(author, "No replica in channel: " + channel, isWhisper());
            } else {
                replica.stop();
                engine.replicasMappedByChannel.remove(channel);
                log.info("Successfully shut down replica in channel: {}", channel);
                outService.enqueueMessageForSending(author, "Successfully shut down replica in channel: " + channel, isWhisper());
            }
        }

        log.info("Executed [replicaoff] command by user: {}, channel: {}", author, channel);
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
