package org.saturn.app.model.command.impl;

import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.impl.ListCommandListenerImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.JoinChannelListener;
import org.saturn.app.service.impl.OutService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@CommandAliases(aliases = {"list", "l"})
public class ListUserCommandImpl extends UserCommandBaseImpl {

    private final OutService outService;

    private final List<String> aliases = new ArrayList<>();

    public ListUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getWhiteListedTrips(engine));
        super.setAliases(this.getAliases());
        this.outService = super.engine.getOutService();
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
        String author = super.chatMessage.getNick();

        List<String> arguments = this.getArguments();
        String channel;
        if (arguments.size() > 0) {
            channel = arguments.get(0);
        } else {
            outService.enqueueMessageForSending("Example: " + engine.prefix + "list programming");
            return;
        }

        if (channel.equals(engine.channel)) {
            /* parse nicks from current channel */
            printUsers(author, engine.currentChannelUsers, engine.outService);
        } else {
            /* ListCommandListenerImpl will make sure to close the connection */
            joinChannel(author, channel);
        }
    }

    private void joinChannel(String author, String channel) {
        EngineImpl listBot = new EngineImpl(null, null, false); // no db connection, nor config for this one is needed
        setupListBot(channel, listBot);

        JoinChannelListener joinChannelListener = new ListCommandListenerImpl(new JoinChannelListenerDto(this.engine, author, channel));
        listBot.setListCommandListener(joinChannelListener);

        listBot.start();
    }

    public static void printUsers(String author, List<User> users, OutService outService) {
        StringBuilder output = new StringBuilder();
        users.forEach(user -> output.append(user.getHash()).append(" - ").append(user.getTrip() == null || Objects.equals(user.getTrip(), "") ? "------" : user.getTrip()).append(" - ").append(user.getNick()).append("\\n"));

        outService.enqueueMessageForSending("@" + author + "\\nUsers online: \\n" + output + "\\n");
    }

    private void setupListBot(String channel, EngineImpl listBot) {
        listBot.isMain = false;
        listBot.setChannel(channel);
        int length = 8;
        boolean useLetters = true;
        boolean useNumbers = true;
        String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);
        listBot.setNick(generatedNick);
        listBot.setPassword(engine.trip);
    }
}
