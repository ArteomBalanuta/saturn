package org.saturn.app.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.listener.impl.ListCommandListenerImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.OutService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@Slf4j
@CommandAliases(aliases = {"list", "l"})
public class ListUserCommandImpl extends UserCommandBaseImpl {

    private final OutService outService;

    private final List<String> aliases = new ArrayList<>();

    public ListUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
        return Role.TRUSTED;
    }

    @Override
    public Optional<Status> execute() {
        String author = super.chatMessage.getNick();

        List<String> arguments = this.getArguments();
        if (arguments.isEmpty()) {
            printUsers(author, engine.currentChannelUsers, engine.outService, chatMessage.isWhisper());
            outService.enqueueMessageForSending(author, "Example: " + engine.prefix + "list programming", isWhisper());
            return Optional.of(Status.FAILED);
        }

        String channel = arguments.get(0).trim();
        if (channel.isBlank() || channel.equals(engine.channel)) {
            /* parse nicks from current channel */
            printUsers(author, engine.currentChannelUsers, engine.outService, chatMessage.isWhisper());
        } else {
            /* ListCommandListenerImpl will make sure to close the connection */
            joinChannel(author, channel);
        }

        return Optional.of(Status.SUCCESSFUL);
    }

    public void joinChannel(String author, String channel) {
        Configuration main = super.engine.getConfig();
        EngineImpl slaveEngine = new EngineImpl(null, main, false); // no db connection, nor config for this one is needed
        setupEngine(channel, slaveEngine);

        JoinChannelListener onlineSetListener = new ListCommandListenerImpl(new JoinChannelListenerDto(this.engine, slaveEngine, author, channel));
        onlineSetListener.setChatMessage(chatMessage);

        slaveEngine.setOnlineSetListener(onlineSetListener);

        slaveEngine.start();
    }

    public void printUsers(String author, List<User> users, OutService outService, boolean isWhisper) {
        StringBuilder output = new StringBuilder();
        users.forEach(user -> output.append(user.getHash()).append(" - ").append(user.getTrip() == null || Objects.equals(user.getTrip(), "") ? "------" : user.getTrip()).append(" - ").append(user.getNick()).append("\\n"));

        outService.enqueueMessageForSending(author, "\\nUsers online: \\n" + output + "\\n", isWhisper);
    }

    public void setupEngine(String channel, EngineImpl listBot) {
        listBot.isMain = false;
        listBot.setChannel(channel);
        int length = 8;
        boolean useLetters = true;
        boolean useNumbers = true;
        String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);
        listBot.setNick(generatedNick);
        listBot.setPassword(engine.password);
    }
}
