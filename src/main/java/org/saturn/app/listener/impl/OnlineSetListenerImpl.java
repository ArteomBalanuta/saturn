package org.saturn.app.listener.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;

import java.util.Arrays;
import java.util.List;

import static org.saturn.app.util.Util.gson;


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
        JsonElement listingElement = e.getAsJsonObject().get("users");
        User[] users = gson.fromJson(listingElement, User[].class);
        engine.setActiveUsers(Arrays.asList(users));
        if (engine.engineType.equals(EngineType.HOST)) {
            engine.outService.enqueueMessageForSending("/color BF40BF");
            executeStartupCommands();
        }
    }

    private void executeStartupCommands() {
        if (StringUtils.isNotBlank(engine.autorunCmds)) {
            log.warn("Startup commands to be executed: {}", engine.autorunCmds);

            List<String> autorunCommands = List.of(engine.autorunCmds.split(","));
            autorunCommands.forEach(command -> {
                log.warn("Executing autorun command: {}", command);
                engine.outService.enqueueMessageForSending("/whisper " + engine.nick + " " + engine.getPrefix() + command);
            });
        }
    }
}
