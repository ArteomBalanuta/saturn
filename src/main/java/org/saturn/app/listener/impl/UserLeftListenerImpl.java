package org.saturn.app.listener.impl;

import static org.saturn.app.util.Util.gson;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;

public class UserLeftListenerImpl implements Listener {
  @Override
  public String getListenerName() {
    return "leftListener";
  }

  private final EngineImpl engine;

  public UserLeftListenerImpl(EngineImpl engine) {
    this.engine = engine;
  }

  @Override
  public void notify(String jsonText) {
    JsonElement element = JsonParser.parseString(jsonText);
    JsonObject object = element.getAsJsonObject();

    User user = gson.fromJson(object, User.class);
    engine.removeActiveUser(user.getNick());
  }
}
