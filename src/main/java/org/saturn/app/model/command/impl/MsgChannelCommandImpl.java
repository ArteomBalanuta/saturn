package org.saturn.app.model.command.impl;

import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.impl.MsgChannelCommandListenerImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.JoinChannelListener;
import org.saturn.app.service.impl.OutService;

import java.util.ArrayList;
import java.util.List;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@CommandAliases(aliases = {"msgchannel", "msgroom"})
public class MsgChannelCommandImpl extends UserCommandBaseImpl {

    private final OutService outService;

    private final List<String> aliases = new ArrayList<>();

    public MsgChannelCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
            channel = arguments.get(0).replace("?","");
        } else {
            outService.enqueueMessageForSending("Example: " + engine.prefix + "msg programming <hello_world>");
            return;
        }

        StringBuilder message = new StringBuilder();
        for (int i = 1; i < arguments.size(); i++) {
            message.append(' ').append(arguments.get(i));
        }

        System.out.println("Message to be delivered: " + message);

        if (channel.equals(engine.channel)) {
            /* msg current channel */
            outService.enqueueMessageForSending("anonymous mail from: ?" + engine.channel + " message: " + message);
        } else {
            /* JoinChannelListener will make sure to close the connection */
            EngineImpl listBot = new EngineImpl(null, null, false); // no db connection, nor config for this one is needed
            setupListBot(channel, listBot);

            JoinChannelListener joinChannelListener = new MsgChannelCommandListenerImpl(new JoinChannelListenerDto(this.engine, author, channel));
            joinChannelListener.setAction(() -> {
                listBot.outService.enqueueMessageForSending("anonymous mail from: ?" + engine.channel + " ,message: \\n" + message);
                listBot.shareMessages();
                outService.enqueueMessageForSending("@" + author + " package delivered.");
            });

            listBot.setListCommandListener(joinChannelListener);
            listBot.start();
        }
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
