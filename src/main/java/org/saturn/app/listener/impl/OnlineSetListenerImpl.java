package org.saturn.app.listener.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;

import java.util.Arrays;

import static org.saturn.app.util.DateUtil.getTimestampNow;
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
        if (engine.isMain) {
            engine.outService.enqueueMessageForSending("/color #ff6200");
        }
    }
}
