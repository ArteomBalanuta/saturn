package org.saturn.app.listener.impl;

import static org.saturn.app.util.Util.gson;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;

@Slf4j
public class OnlineSetListenerImpl implements Listener {
  @Override
  public String getListenerName() {
    return "onlineSetListener";
  }

  private final EngineImpl engine;

  public OnlineSetListenerImpl(EngineImpl engine) {
    this.engine = engine;
  }

  @Override
  public void notify(String jsonText) {
    JsonElement e = JsonParser.parseString(jsonText);

    JsonElement listingElement = null;
    if (engine.engineType.equals(EngineType.AGENT)) {
      List<JsonElement> nicks = e.getAsJsonObject().getAsJsonArray("nicks").asList();
      Set<User> users =
          nicks.stream()
              .map(
                  n -> {
                    return new User(n.getAsString());
                  })
              .collect(Collectors.toSet());
      engine.setActiveUsers(users.stream().toList());
      return;
    } else {
      listingElement = e.getAsJsonObject().get("users");
    }

    User[] users = gson.fromJson(listingElement, User[].class);
    engine.setActiveUsers(Arrays.asList(users));
    if (engine.engineType.equals(EngineType.HOST)) {
      engine.outService.enqueueMessageForSending("/color FFDF00");
      executeStartupCommands();
    }
  }

  private void executeStartupCommands() {
    if (StringUtils.isNotBlank(engine.autorunCmds)) {
      log.warn("Startup commands to be executed: {}", engine.autorunCmds);

      List<String> autorunCommands = List.of(engine.autorunCmds.split(","));
      autorunCommands.forEach(
          command -> {
            log.warn("Executing autorun command: {}", command);
            engine.outService.enqueueMessageForSending(
                "/whisper " + engine.nick + " " + engine.getPrefix() + command);
          });
    }
  }
}
