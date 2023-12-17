package org.saturn.app.facade;

import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.User;

import java.util.List;

public interface Engine {
    void start();

    void start(Proxy proxy);

    void stop();

    void setBaseWsUrl(String address);

    void setChannel(String channel);
    void setNick(String nick);
    void setPassword(String password);

    void say(String message);

    List<User> getActiveUsers();

    void setActiveUsers(List<User> users);

    void dispatchMessage(String json);


}
