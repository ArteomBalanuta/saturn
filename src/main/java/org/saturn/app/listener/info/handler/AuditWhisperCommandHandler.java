package org.saturn.app.listener.info.handler;

import static org.saturn.app.util.DateUtil.getTimestampNow;

import org.saturn.app.listener.info.InfoMessageContext;
import org.saturn.app.listener.info.InfoMessageHandler;
import org.saturn.app.model.MessageAuditEvent;
import org.saturn.app.model.dto.payload.ChatMessage;

public class AuditWhisperCommandHandler implements InfoMessageHandler {

  @Override
  public boolean handle(InfoMessageContext context) {
    if (context.getChatMessage().isEmpty()) {
      return false;
    }

    ChatMessage chatMessage = context.getChatMessage().get();
    context
        .getEngine()
        .logRepository
        .logMessage(
            MessageAuditEvent.whisper(
                chatMessage.getTrip(),
                chatMessage.getNick(),
                chatMessage.getHash(),
                chatMessage.getText(),
                context.getEngine().channel,
                getTimestampNow()));
    return true;
  }
}
