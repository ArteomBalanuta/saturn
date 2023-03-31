package org.saturn.app.listener.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.ChatMessage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.saturn.app.util.Util.getTimestampNow;
import static org.saturn.app.util.Util.gson;

public class UserMessageListenerImpl implements Listener {

    private final EngineImpl engine;

    public UserMessageListenerImpl(EngineImpl engine) {
        this.engine = engine;
    }

    @Override
    public String getListenerName() {
        return "messageListener";
    }

    @Override
    public void notify(String jsonText) {
        ChatMessage message = gson.fromJson(jsonText, ChatMessage.class);
        System.out.println(message.getNick() + ": " + message.getText() + " | " + Arrays.toString(message.getText().getBytes(StandardCharsets.UTF_8)));

        engine.logService.logMessage(message.getTrip(), message.getNick(), message.getHash(), message.getText(),
                getTimestampNow());

        boolean isBotMessage = message.getNick().equals(engine.nick);
        if (isBotMessage) {
            System.out.println("returned cause bot message");
            return;
        }

        /* Mail service check */
        String author = message.getNick();
        engine.deliverMailIfPresent(author);

        String cmd = message.getText().trim();
        if (!cmd.startsWith(engine.prefix)) {
            return;
        }

        UserCommand userCommand = new UserCommandBaseImpl(message, engine, engine.whiteList);
        userCommand.execute();
    }
}
