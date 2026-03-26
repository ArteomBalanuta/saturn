package org.saturn.app.listener.message.handler;

import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandler;

public class CernEasterEggHandler implements ChatMessageHandler {

  @Override
  public boolean handle(ChatMessageContext context) {
    if (context.getMessage().getText().toLowerCase().contains("has cern ended the universe")) {
      context.getEngine().outService.enqueueMessageForSending("no");
    }
    return true;
  }
}
