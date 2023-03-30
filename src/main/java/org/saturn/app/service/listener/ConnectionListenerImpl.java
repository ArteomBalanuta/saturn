package org.saturn.app.service.listener;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.ChatMessage;
import org.saturn.app.service.Listener;

import static org.saturn.app.util.Util.getTimestampNow;
import static org.saturn.app.util.Util.gson;

public class ConnectionListenerImpl implements Listener {
    @Override
    public String getListenerName() {
        return "connectionListener";
    }

    private final EngineImpl engine;

    public ConnectionListenerImpl(EngineImpl engine) {
        this.engine = engine;
    }

    @Override
    public void notify(String jsonText) {
        if ("connected".equals(jsonText)) {
            engine.sendJoinMessage();
            return;
        }

        engine.dispatchMessage(jsonText);
    }
}
