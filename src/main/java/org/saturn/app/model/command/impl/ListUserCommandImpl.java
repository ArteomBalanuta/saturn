package org.saturn.app.model.command.impl;

import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.service.ListCommandListener;
import org.saturn.app.service.impl.OutService;
import org.saturn.app.listener.impl.ListCommandListenerImpl;

import java.util.List;
import java.util.Objects;

public class ListUserCommandImpl extends UserCommandBaseImpl {
    private final OutService outService;

    public ListUserCommandImpl(EngineImpl engine, List<String> whiteListedTrips) {
        super(null, engine, whiteListedTrips);
        super.setCommandNames(this.getCommandNames());
        this.outService = super.engine.getOutService();
    }

    @Override
    public List<String> getCommandNames() {
        return List.of("list","l");
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
            // parse nicks from current channel
            printUsers(author, engine.currentChannelUsers, engine.outService);
        } else {
            /* ListCommandListenerImpl will make sure to close the connection */
            joinChannel(author, channel);
        }
    }

    private void joinChannel(String author, String channel) {
        EngineImpl listBot = new EngineImpl(null, null, false); // no db connection, nor config for this one is needed
        setupListBot(channel, listBot);

        ListCommandListener listCommandListener = new ListCommandListenerImpl(new Dto(this.engine, author, channel));
        listBot.setListCommandListener(listCommandListener);

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

    public static class Dto {
        public EngineImpl engine;
        public String author;
        public String channel;

        public Dto(EngineImpl engine, String author, String channel) {
            this.engine = engine;
            this.author = author;
            this.channel = channel;
        }
    }
}
