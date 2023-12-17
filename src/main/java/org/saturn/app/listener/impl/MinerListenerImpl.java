package org.saturn.app.listener.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;
import org.saturn.app.util.Util;

import java.util.*;
import java.util.stream.Collectors;

public class MinerListenerImpl implements Listener {
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
        List<User> users = Util.getUsers(jsonText);
        if (engine.isMain) {
            engine.stop();
            throw new RuntimeException("Shouldn't be used with main threat!");
        }

        Set<User> result = users.stream()
                .filter(user -> Objects.equals(user.getNick(), engine.nick))
                .collect(Collectors.toSet());

        Optional<User> first = result.stream().findFirst();
        if (first.isEmpty()) {
            throw new RuntimeException("Didn't join");
        }

        System.out.println("Password: " + engine.password + " trip: " + first.get().getTrip());

        engine.stop();
    }
}
