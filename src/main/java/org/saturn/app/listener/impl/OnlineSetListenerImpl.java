package org.saturn.app.listener.impl;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.listener.snapshot.GsonOnlineSetPayloadParser;
import org.saturn.app.listener.snapshot.OnlineSetSnapshot;
import org.saturn.app.listener.snapshot.PayloadDecodeException;

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
    try {
      OnlineSetSnapshot snapshot =
          new GsonOnlineSetPayloadParser(engine.engineType, "permanent", engine.channel)
              .parse(jsonText);
      engine.setActiveUsers(snapshot.users());
      if (engine.engineType.equals(EngineType.HOST)) {
        executeStartupCommands();
      }
    } catch (PayloadDecodeException | RuntimeException e) {
      log.error("Failed to decode onlineSet for channel {}", engine.channel, e);
    }
  }

  private void executeStartupCommands() {
    if (StringUtils.isNotBlank(engine.autorunCmds)) {
      log.warn("Startup commands to be executed: {}", engine.autorunCmds);

      List<String> autorunCommands = List.of(engine.autorunCmds.split(","));
      for (String command : autorunCommands) {
        log.warn("Executing autorun command: {}", command);
        if (command.startsWith("/")) {
          engine.outService.enqueueMessageForSending(command);
        } else {
          engine.outService.enqueueMessageForSending(
              "/whisper " + engine.nick + " " + engine.getPrefix() + command);
        }
      }
    }
  }
}
