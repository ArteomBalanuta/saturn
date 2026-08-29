package org.saturn.app.listener.info.handler;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.listener.info.InfoMessageContext;
import org.saturn.app.listener.info.InfoMessageHandler;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
public class ConvertWhisperToChatMessageHandler implements InfoMessageHandler {

  @Override
  public boolean handle(InfoMessageContext context) {
    Optional<String> author = Optional.ofNullable(context.getInfoMessage().getFrom());
    if (author.isEmpty()) {
      log.warn("Received info message: {}, from server", context.getInfoMessage().getJson());
      return false;
    }

    String[] split = context.getInfoMessage().getText().split(author.get() + " whispered: ");
    if (split.length <= 1) {
      return false;
    }

    User user =
        context.getEngine().currentChannelUsers.stream()
            .filter(u -> u.getNick().equals(author.get()))
            .findFirst()
            .orElseThrow();

    ChatMessage chatMessage =
        new ChatMessage(null, author.get(), user.getTrip(), user.getHash(), null, split[1]);
    chatMessage.setWhisper(true);

    log.info(
        "Received whisper: {}, from: {}, trip: {}, hash: {} ",
        split[1],
        author.get(),
        context.getInfoMessage().getTrip(),
        user.getHash());
    context.setChatMessage(Optional.of(chatMessage));
    return true;
  }
}
