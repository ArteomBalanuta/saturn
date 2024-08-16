package org.saturn.app.model.command.impl;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.listener.impl.KickCommandListenerImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;

import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"move", "recover", "heal", "resurrect"})
public class ResurrectUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public ResurrectUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getAdminTrips(engine));
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
        List<String> arguments = getArguments();
        if (arguments.size() != 3) {
            super.engine.outService.enqueueMessageForSending(chatMessage.getNick(), " " + engine.prefix + "move <nick> <from> <to>", isWhisper());
        }

        String from = arguments.get(1);
        String target = arguments.get(0);
        String to = arguments.get(2);

        resurrect(from, target, to);
    }

    public void resurrect(String channel, String nick, String targetChannel) {
        Configuration main = super.engine.getConfig();
        EngineImpl slaveEngine = new EngineImpl(null, main, false);
        setupEngine(channel, slaveEngine);

        JoinChannelListenerDto dto = new JoinChannelListenerDto(this.engine, slaveEngine, slaveEngine.nick, channel);
        dto.target = nick;
        dto.destinationRoom = targetChannel;

        JoinChannelListener onlineSetListener = new KickCommandListenerImpl(dto);
        onlineSetListener.setChatMessage(chatMessage);

        onlineSetListener.setAction(() -> {
            slaveEngine.outService.enqueueRawMessageForSending(String.format("{ \"cmd\": \"kick\", \"nick\": \"%s\", \"to\":\"%s\"}", nick, targetChannel));
            slaveEngine.shareMessages();
        });

        slaveEngine.setOnlineSetListener(onlineSetListener);
        slaveEngine.start();
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
