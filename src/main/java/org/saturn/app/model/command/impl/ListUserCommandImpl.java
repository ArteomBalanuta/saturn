package org.saturn.app.model.command.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.facade.Engine;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.service.impl.OutService;
import org.saturn.app.util.Cmd;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

import static org.saturn.app.util.Util.sleep;

public class ListUserCommandImpl extends UserCommandBaseImpl {
    private final OutService outService;

    public ListUserCommandImpl(EngineImpl engine, List<String> whiteListedTrips) {
        super(null, engine, whiteListedTrips);
        super.setCommandName(this.getCommandName());
        this.outService = super.engine.getOutService();
    }

    @Override
    public String getCommandName() {
        return "list";
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

        List<String> nickList;
        if (channel.equals(engine.channel)) {
            // parse nicks from current channel
            String userNames = engine.getActiveUsers().stream()
                    .map(user -> user.getTrip() + " " + user.getNick())
                    .collect(Collectors.toList())
                    .toString();

            outService.enqueueMessageForSending("@" + author + "\\n```Text \\n Users online: " + userNames + "\\n" +
                    " ```");
        } else {
            nickList = getNicksFromChannel(channel).stream().map(user -> user.getTrip() + " " + user.getNick()).collect(Collectors.toList());
            if (nickList.isEmpty()) {
                outService.enqueueMessageForSending("@" + author + " " + channel + " is empty");
            } else {
                outService.enqueueMessageForSending("@" + author + " " + nickList);
            }
        }
    }

    private List<User> getNicksFromChannel(String channel) {
        Engine listBot = new EngineImpl(null, null, false); // no db connection, nor config for this one is needed
        listBot.setChannel(channel);

        int length = 8;
        boolean useLetters = true;
        boolean useNumbers = true;
        String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);
        listBot.setNick(generatedNick);
        listBot.setPassword(engine.trip);

        listBot.start();

        while (!listBot.isJoined()) {
            sleep(50);
        }

        List<User> activeUsers = listBot.getActiveUsers();

        listBot.stop();

        return activeUsers;
    }
}
