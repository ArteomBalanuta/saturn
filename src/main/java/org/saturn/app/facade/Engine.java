package org.saturn.app.facade;

import java.util.List;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.User;

public interface Engine {
  void start();

  void start(Proxy proxy);

  void stop();

  boolean isConnected();

  void setBaseWsUrl(String address);

  void setChannel(String channel);

  void setNick(String nick);

  void setPassword(String password);

  void setActiveUsers(List<User> users);

  void dispatchMessage(String json);

  void addReplica(EngineImpl engine);
}
