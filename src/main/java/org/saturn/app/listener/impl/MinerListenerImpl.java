package org.saturn.app.listener.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;
import org.saturn.app.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

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

        String raw = "Password: " + engine.password + " trip: " + first.get().getTrip() + " \r\n";
        try {
            Files.write(Paths.get(fileName),
                    raw.getBytes(),
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        engine.stop();
    }
}
