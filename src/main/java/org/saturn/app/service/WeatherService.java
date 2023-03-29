package org.saturn.app.service;

import org.saturn.app.model.command.UserCommand;

public interface WeatherService {
    void executeWeather(String owner, UserCommand cmd);
}
