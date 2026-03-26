package org.saturn.app.listener.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;
import org.saturn.app.util.Util;

public class MinerListenerImpl implements Listener {

  public String fileName = "trips.txt";

  @Override
  public String getListenerName() {
    return "minerChannelListener";
  }

  private final EngineImpl engine;

  public MinerListenerImpl(EngineImpl engine) {
    this.engine = engine;
  }

  @Override
  public void notify(String jsonText) {
    List<User> users = Util.extractUsersFromJson(jsonText);
    if (engine.engineType.equals(EngineType.HOST)) {
      engine.stop();
      throw new RuntimeException("Shouldn't be used with main threat!");
    }

    User joinedUser = null;
    for (User user : users) {
      if (Objects.equals(user.getNick(), engine.nick)) {
        joinedUser = user;
        break;
      }
    }
    if (joinedUser == null) {
      throw new RuntimeException("Didn't join");
    }

    String raw = "Password: " + engine.password + " trip: " + joinedUser.getTrip() + " \r\n";
    try {
      Files.write(Paths.get(fileName), raw.getBytes(), StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    engine.stop();
  }
}
