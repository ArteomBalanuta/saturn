package org.saturn.app.listener.message;

import java.util.List;

public class ChatMessageHandlerChain {
  private final List<ChatMessageHandler> handlers;

  public ChatMessageHandlerChain(List<ChatMessageHandler> handlers) {
    this.handlers = handlers;
  }

  public void process(ChatMessageContext context) {
    for (ChatMessageHandler handler : handlers) {
      if (!handler.handle(context)) {
        return;
      }
    }
  }
}
