package org.saturn.app.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.command.UserCommandBaseImpl;
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
    public Role getAuthorizedRole() {
        return Role.REGULAR;
    }
    @Override
    public Optional<Status> execute() {
        String weather = engine.weatherService.getWeather(getArguments());
        if (weather == null) {
            log.error("Weather API is not responding, arguments: {}", getArguments());
            return Optional.of(Status.FAILED);
        }
        String weatherAligned = Util.alignWithWhiteSpace(weather,":", "&nbsp;", false);
        engine.outService.enqueueMessageForSending(chatMessage.getNick(), weatherAligned, isWhisper());
        return Optional.of(Status.SUCCESSFUL);
    }
}
