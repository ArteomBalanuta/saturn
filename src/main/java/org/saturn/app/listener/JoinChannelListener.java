package org.saturn.app.listener;

import org.saturn.app.model.dto.payload.ChatMessage;

public interface JoinChannelListener extends Listener {
  String getListenerName();
  default void setAction(Runnable operation) {}
  default void runAction() {}
  default void setChatMessage(ChatMessage chatMessage) {}
}
