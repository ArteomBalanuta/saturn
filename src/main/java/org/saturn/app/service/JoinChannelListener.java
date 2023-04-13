package org.saturn.app.service;

import org.saturn.app.model.dto.User;

import java.util.List;

public interface JoinChannelListener {
    String getListenerName();
    void notify(List<User> users);

    default void setAction(Runnable operation) {}
    default void runAction() {}
}
