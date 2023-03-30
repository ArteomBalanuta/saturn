package org.saturn.app.service.listener;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.saturn.app.facade.Engine;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.ChatMessage;
import org.saturn.app.model.dto.User;
import org.saturn.app.service.Listener;

import java.util.Arrays;

import static org.saturn.app.util.Util.*;

public class UserJoinedListenerImpl implements Listener {
    @Override
    public String getListenerName() {
        return "joinListener";
    }
    private final EngineImpl engine;

    public UserJoinedListenerImpl(EngineImpl engine) {
        this.engine = engine;
    }

    @Override
    public void notify(String jsonText) {
        JsonElement element = JsonParser.parseString(jsonText);
        JsonObject object = element.getAsJsonObject();
        User user = gson.fromJson(object, User.class);
        System.out.println("Joined: " + user.toString());

        engine.addActiveUser(user);
        engine.shareUserInfo(user);
        engine.proceedBanned(user);
    }
}
