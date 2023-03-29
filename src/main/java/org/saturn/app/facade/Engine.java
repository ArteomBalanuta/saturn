package org.saturn.app.facade;

import org.saturn.app.model.dto.User;

import java.util.List;

public interface Engine {
    void start();
    void stop();

    void setBaseWsUrl(String address);

    void setChannel(String channel);
    void setNick(String nick);
    void setPassword(String password);

    void say(String message);

    List<User> getActiveUsers();

    boolean isJoined();
}
