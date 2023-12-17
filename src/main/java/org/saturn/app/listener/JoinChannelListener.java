package org.saturn.app.listener;

import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;

import java.util.List;

public interface JoinChannelListener extends Listener {
    String getListenerName();

    default void setAction(Runnable operation) {}
    default void runAction() {}
}
