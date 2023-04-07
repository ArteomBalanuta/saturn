package org.saturn.app.listener.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.model.dto.payload.InfoMessage;

import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getTimestampNow;
import static org.saturn.app.util.Util.gson;

public class InfoMessageListenerImpl implements Listener {

    private final EngineImpl engine;

    public InfoMessageListenerImpl(EngineImpl engine) {
        this.engine = engine;
    }

    @Override
    public String getListenerName() {
        return "infoMessageListener";
    }

    @Override
    public void notify(String jsonText) {
        InfoMessage message = gson.fromJson(jsonText, InfoMessage.class);

        if (message.getFrom().equals(engine.nick) || message.getText().contains("You whispered")) {
            return;
        }

        processRename(message);

        ChatMessage chatMessage = infoToChatMessages(message);
        if (chatMessage == null) {
            return;
        }

        String cmd = chatMessage.getText().trim();
        if (!cmd.startsWith(engine.prefix)) {
            return;
        }

        engine.logService.logMessage(chatMessage.getTrip(), chatMessage.getNick(), chatMessage.getHash(), chatMessage.getText(), getTimestampNow());

        /* empty whitelist */
        UserCommand userCommand = new UserCommandBaseImpl(chatMessage, engine, List.of());
        userCommand.execute();

    }

    private void processRename(InfoMessage message) {
        String text = message.getText();
        if (text.contains(" is now ")) {

            engine.logService.logMessage("","", "", text, getTimestampNow());

            String[] split = text.split(" is now ");
            String before = split[0];
            String after = split[1];

            if (before.equals(engine.nick)) {
                engine.nick = after;
            }
        }
    }

    private static ChatMessage infoToChatMessages(InfoMessage message) {
        Optional<String> author = Optional.ofNullable(message.getFrom());
        if (author.isEmpty()) {
            return null;
        }
        String[] split = message.getText().split(author.get() + " whispered: ");
        if (split.length > 0) {
            String text = split[1];
            ChatMessage chatMessage = new ChatMessage(null, author.get(), message.getTrip(), null, null, text);
            chatMessage.setWhisper(true);

            return chatMessage;
        }
        return null;
    }
}
