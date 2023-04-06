package org.saturn.app.listener.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.ChatMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
        System.out.println(message.getNick() + ": " + message.getText());

        if (message.getCmd().equals("info")) {
            message.setNick("");
        }

        engine.logService.logMessage(message.getTrip(), message.getNick(), message.getHash(), message.getText(),
                getTimestampNow());

        processWhisperMessages(message);

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

        /* empty whitelist */
        UserCommand userCommand = new UserCommandBaseImpl(message, engine, List.of());
        userCommand.execute();
    }

    private static void processWhisperMessages(ChatMessage message) {
        if ("info".equals(message.getCmd())) {
            Optional<String> author = Optional.ofNullable(message.getFrom());
            if (author.isEmpty()) {
                return;
            }
            String[] split = message.getText().split(author.get() + " whispered: ");
            if (split.length > 0) {
                String text = split[1];
                message.setNick(author.get());
                message.setText(text);
                message.setWhisper(true);
            }
        }
    }
}
