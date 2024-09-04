package org.saturn.app.service;

import java.util.List;

public interface WeatherService {
    /*
        http://api.geonames.org/search?q=london&maxRows=1&username=dev1
        https://api.open-meteo.com/v1/forecast?
        username: mercury389
         */
    String getWeather(List<String> arguments);
}
