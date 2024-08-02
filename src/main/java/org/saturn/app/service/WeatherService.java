package org.saturn.app.service;

import org.saturn.app.model.command.UserCommand;

import java.util.List;

public interface WeatherService {
    void executeWeather(String owner, List<String> arguments);
}
