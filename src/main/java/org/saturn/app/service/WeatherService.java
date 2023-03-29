package org.saturn.app.service;

import org.saturn.app.model.Command;

public interface WeatherService {
    void executeWeather(String owner, Command cmd);
}
