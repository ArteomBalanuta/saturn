package org.saturn.app.listener;

import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.List;

public interface JoinChannelListener extends Listener {
    String getListenerName();

    default void setAction(Runnable operation) {}
    default void runAction() {}

    default void setChatMessage(ChatMessage chatMessage) {}
}
