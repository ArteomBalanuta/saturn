package org.saturn.app.listener.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.model.dto.payload.InfoMessage;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.saturn.app.util.DateUtil.getTimestampNow;
import static org.saturn.app.util.Util.gson;

@Slf4j
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
        message.setJson(jsonText);

        if (engine.nick.equals(message.getFrom()) || message.getText().contains("You whispered")) {
            return;
        }

        processRename(message);

        ChatMessage chatMessage = infoToChatMessages(message);
        if (chatMessage == null) {
            return;
        }

        engine.logService.logMessage(chatMessage.getTrip(), chatMessage.getNick(), chatMessage.getHash(), chatMessage.getText(), getTimestampNow());

        String cmd = chatMessage.getText().trim();
        if (!cmd.startsWith(engine.prefix)) {
            return;
        }

        /* empty whitelist */
        log.info("Possible whisper cmd: {}", cmd);

        chatMessage.setWhisper(true);
        UserCommandBaseImpl userCommandBase = new UserCommandBaseImpl(chatMessage, engine, List.of());

        ((UserCommand) userCommandBase).execute();
    }

    private void processRename(InfoMessage message) {
        String text = message.getText();
        if (text.contains(" is now ")) {
            String[] split = text.split(" is now ");
            String before = split[0];
            String after = split[1];

            log.warn("User renamed from: {} to {}", before, after);
            if (before.equals(engine.nick)) {
                engine.nick = after;
            }
        }
    }

    private ChatMessage infoToChatMessages(InfoMessage message) {
        Optional<String> author = Optional.ofNullable(message.getFrom());
        if (author.isEmpty()) {
            log.warn("Received info message: {}, from server", message.getJson());
            return null;
        }
        String[] split = message.getText().split(author.get() + " whispered: ");
        if (split.length > 0) {
            String text = split[1];

            /* should be always present as we cant receive a whisper from a non present user */
            User user = engine.currentChannelUsers.stream().filter(u -> u.getNick().equals(author.get())).findFirst().get();

            ChatMessage chatMessage = new ChatMessage(null, author.get(), user.getTrip(), user.getHash(), null, text);
            chatMessage.setWhisper(true);

            log.info("Received whisper: {}, from: {}, trip: {}, hash: {} ", text, author.get(), message.getTrip(), user.getHash());
            return chatMessage;
        }
        return null;
    }
}
