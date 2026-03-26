package org.saturn.app.listener.info.handler;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.listener.info.InfoMessageContext;
import org.saturn.app.listener.info.InfoMessageHandler;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
public class DispatchWhisperCommandHandler implements InfoMessageHandler {

  @Override
  public boolean handle(InfoMessageContext context) {
    if (context.getChatMessage().isEmpty()) {
      return false;
    }

    ChatMessage chatMessage = context.getChatMessage().get();
    String cmd = chatMessage.getText().trim();
    if (!cmd.startsWith(context.getEngine().prefix)) {
      return false;
    }

    log.info("Possible whisper cmd: {}", cmd);
    chatMessage.setWhisper(true);
    UserCommandBaseImpl userCommandBase =
        new UserCommandBaseImpl(chatMessage, context.getEngine(), List.of());
    userCommandBase.execute();
    return false;
  }
}
