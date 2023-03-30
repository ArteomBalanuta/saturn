package org.saturn.app.service;

import org.saturn.app.model.dto.User;

import java.util.List;

public interface ListCommandListener {
    String getListenerName();
    void notify(List<User> users);
}
