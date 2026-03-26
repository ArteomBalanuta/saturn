package org.saturn.app.listener.message.handler;

import static org.saturn.app.util.DateUtil.getTimestampNow;

import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandler;
import org.saturn.app.model.dto.payload.ChatMessage;

public class AuditChatMessageHandler implements ChatMessageHandler {

  @Override
  public boolean handle(ChatMessageContext context) {
    ChatMessage message = context.getMessage();
    context.getEngine()
        .logRepository
        .logMessage(
            message.getTrip(),
            message.getNick(),
            message.getHash(),
            message.getText(),
            context.getEngine().channel,
            getTimestampNow());
    return true;
  }
}
