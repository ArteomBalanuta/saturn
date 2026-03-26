package org.saturn.app.listener.message;

public interface ChatMessageHandler {
  boolean handle(ChatMessageContext context);
}
