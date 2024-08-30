package org.saturn.app.listener.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.List;

import static org.saturn.app.util.DateUtil.getTimestampNow;
import static org.saturn.app.util.Util.gson;


@Slf4j
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
        log.debug("Full message payload: {}", jsonText);

        ChatMessage message = gson.fromJson(jsonText, ChatMessage.class);


        engine.logService.logMessage(message.getTrip(), message.getNick(), message.getHash(), message.getText(),
                getTimestampNow());

        boolean isBotMessage = engine.nick.equals(message.getNick());
        if (isBotMessage) {
            return;
        }

        log.info("hash: {}, trip: {}, nick: {}, message: {}", message.getHash(), message.getTrip(), message.getNick(), message.getText());

        /* Mail service check */
        engine.deliverMailIfPresent(message.getNick(), message.getTrip());

        /* Check if user is afk */
        engine.notifyUserNotAfkAnymore(message.getNick());

        /* notify mentioned user is afk currently */
        engine.notifyIsAfkIfUserIsMentioned(message.getNick(), message.getText());

        String cmd = message.getText().trim();
        if (!cmd.startsWith(engine.prefix)) {
            return;
        }
        UserCommand userCommand = new UserCommandBaseImpl(message, engine, List.of());
        userCommand.execute();
    }
}
