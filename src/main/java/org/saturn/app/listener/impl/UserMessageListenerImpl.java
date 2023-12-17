package org.saturn.app.listener.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.List;

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

        engine.logService.logMessage(message.getTrip(), message.getNick(), message.getHash(), message.getText(),
                getTimestampNow());

        boolean isBotMessage = engine.nick.equals(message.getNick());
        if (isBotMessage) {
            return;
        }

        /* Mail service check */
        engine.deliverMailIfPresent(message.getNick(), message.getTrip());

        String cmd = message.getText().trim();
        if (!cmd.startsWith(engine.prefix)) {
            return;
        }

        UserCommand userCommand = new UserCommandBaseImpl(message, engine, List.of());
        userCommand.execute();
    }
}
