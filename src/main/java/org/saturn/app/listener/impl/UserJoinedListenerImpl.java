package org.saturn.app.listener.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.impl.moderator.AutoMoveUserCommandImpl;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;

import static org.saturn.app.command.impl.moderator.AutoMoveUserCommandImpl.AUTHORIZED_LOUNGE_TRIPS;
import static org.saturn.app.command.impl.moderator.AutoMoveUserCommandImpl.CHANNEL;
import static org.saturn.app.command.impl.moderator.AutoMoveUserCommandImpl.SOURCE_CHANNEL;
import static org.saturn.app.util.Util.gson;

@Slf4j
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
        log.info("Joined user, nick: {}, trip: {}, hash: {}, channel: {}", user.getNick(), user.getTrip(), user.getHash(), user.getChannel());

        engine.addActiveUser(user);
        engine.shareUserInfo(user);
        engine.proceedShadowBanned(user);
        /* AutoMoveCommand has been triggered */
        if (AutoMoveUserCommandImpl.isAutoMoveStatus() && engine.engineType.equals(EngineType.REPLICA) && engine.channel.equals(SOURCE_CHANNEL)) {
            log.warn("AutoMoveCommand feature flag is true");
            if (AUTHORIZED_LOUNGE_TRIPS.contains(user.getTrip())) {
                engine.outService.enqueueMessageForSending(user.getNick(), " your trip is authorized to join ?lounge, you will be moved to ?lounge", false);
                engine.modService.kickTo(user.getNick(), CHANNEL);
                log.info("User: {}, has been moved to: {}", user.getNick(), CHANNEL);
            }
        }
    }
}
