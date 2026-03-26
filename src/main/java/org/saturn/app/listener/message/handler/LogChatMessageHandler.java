package org.saturn.app.listener.message.handler;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandler;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
public class LogChatMessageHandler implements ChatMessageHandler {

  @Override
  public boolean handle(ChatMessageContext context) {
    ChatMessage message = context.getMessage();
    log.info(
        "hash: {}, trip: {}, nick: {}, message: {}",
        message.getHash(),
        message.getTrip(),
        message.getNick(),
        message.getText());
    return true;
  }
}
