package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@Slf4j
@CommandAliases(aliases = {"weather", "w", "today"})
public class WeatherUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public WeatherUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getWhiteListedTrips(engine));
        super.setAliases(this.getAliases());
        this.aliases.addAll(aliases);
    }

    @Override
    public List<String> getAliases() {
        return this.aliases;
    }
    @Override
    public List<String> getArguments() {
        return super.getArguments();
    }
    @Override
    public void execute() {
        String weather = engine.weatherService.getWeather(getArguments());
        if (weather == null) {
            log.error("Weather API is not responding, arguments: {}", getArguments());
            return;
        }
        String weatherAligned = Util.alignWithWhiteSpace(weather);
        engine.outService.enqueueMessageForSending(chatMessage.getNick(), weatherAligned, isWhisper());
    }
}
