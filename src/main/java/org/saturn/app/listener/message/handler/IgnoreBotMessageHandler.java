package org.saturn.app.listener.message.handler;

import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandler;

public class IgnoreBotMessageHandler implements ChatMessageHandler {

  @Override
  public boolean handle(ChatMessageContext context) {
    return !context.getEngine().nick.equals(context.getMessage().getNick());
  }
}
