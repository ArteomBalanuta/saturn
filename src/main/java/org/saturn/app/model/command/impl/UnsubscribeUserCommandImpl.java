package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@Slf4j
@CommandAliases(aliases = {"unsub", "unsubscribe"})
public class UnsubscribeUserCommandImpl extends UserCommandBaseImpl {

    private final List<String> aliases = new ArrayList<>();

    public UnsubscribeUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getWhiteListedTrips(engine));
        super.setAliases(this.getAliases());
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
    public void execute() {
        String author = chatMessage.getNick();
        String trip = chatMessage.getTrip();
        if (trip == null && !engine.subscribers.contains(trip)) {
            engine.outService.enqueueMessageForSending(author,"you are not subscribed, please set your trip and use " + engine.prefix + " sub command.", false);
            log.info("User: {} failed unsubing", author);
            return;
        }
        engine.subscribers.remove(trip);
        log.info("User: {}, trip: {}, unsubscribed", author, trip);
        engine.outService.enqueueMessageForSending(author,"your trip will no longer receive hashes and " +
                "nicks for each new joining user. ", true);

        log.info("Executed [unsubscribe] command by user: {}, trip: {}", author, trip);
    }
}
