package org.saturn.app.listener.message.handler;

import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandler;

public class DeliverPendingMailHandler implements ChatMessageHandler {

  @Override
  public boolean handle(ChatMessageContext context) {
    context.getEngine()
        .deliverMailIfPresent(context.getMessage().getNick(), context.getMessage().getTrip());
    return true;
  }
}
