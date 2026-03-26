package org.saturn.app.service.impl;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.dto.Weather;

class WeatherServiceImplTest {

  @Test
  void formatWeatherUsesThinSpaceSeparators() {
    WeatherServiceImpl service = new WeatherServiceImpl(new ArrayBlockingQueue<>(8));

    Weather.Daily daily = new Weather.Daily();
    daily.sunrise = List.of("2026-03-26T06:30");
    daily.sunset = List.of("2026-03-26T18:45");
    daily.uv_index_max = List.of("4");
    daily.shortwave_radiation_sum = List.of("12");

    Weather.DailyUnits dailyUnits = new Weather.DailyUnits();
    dailyUnits.uv_index_max = "idx";
    dailyUnits.shortwave_radiation_sum = "MJ/m2";

    Weather.Hourly hourly = new Weather.Hourly();
    hourly.apparent_temperature = hourlyValues("20");
    hourly.relative_humidity_2m = hourlyValues("35");
    hourly.surface_pressure = hourlyValues("1008");
    hourly.pressure_msl = hourlyValues("1014");
    hourly.shortwave_radiation = hourlyValues("500");
    hourly.diffuse_radiation = hourlyValues("120");
    hourly.soil_temperature_18cm = hourlyValues("15");
    hourly.soil_moisture_3_to_9cm = hourlyValues("0.22");

    Weather.HourlyUnits hourlyUnits = new Weather.HourlyUnits();
    hourlyUnits.apparent_temperature = "C";
    hourlyUnits.relative_humidity_2m = "%";
    hourlyUnits.surface_pressure = "hPa";
    hourlyUnits.pressure_msl = "hPa";
    hourlyUnits.shortwave_radiation = "W/m2";
    hourlyUnits.diffuse_radiation = "W/m2";
    hourlyUnits.soil_temperature_18cm = "C";
    hourlyUnits.soil_moisture_3_to_9cm = "m3/m3";

    Weather.CurrentWeather currentWeather = new Weather.CurrentWeather();
    currentWeather.temperature = "21";
    currentWeather.windspeed = "7";
    currentWeather.weathercode = "0";
    currentWeather.time = "2026-03-26T12:00";

    Weather.CurrentWeatherUnits currentWeatherUnits = new Weather.CurrentWeatherUnits();
    currentWeatherUnits.temperature = "C";
    currentWeatherUnits.windspeed = "km/h";

    Weather weather = new Weather();
    weather.setHourly(hourly);
    weather.setHourly_units(hourlyUnits);
    weather.setCurrent_weather_units(currentWeatherUnits);
    setField(weather, "current_weather", currentWeather);
    setField(weather, "daily", daily);
    setField(weather, "timezone", "UTC");

    String formatted =
        service.formatWeather(
            "London, United Kingdom",
            daily,
            currentWeather,
            dailyUnits,
            weather,
            hourly,
            hourlyUnits,
            currentWeatherUnits);

    Assertions.assertTrue(formatted.contains("\\n"));
    Assertions.assertTrue(formatted.startsWith("Weather forecast for today: **London, United Kingdom**\\n"));
    Assertions.assertTrue(formatted.contains("Temperature: 21 C\\n"));
    Assertions.assertTrue(formatted.contains("\u2009\u2009\u2009 \\n"));
    Assertions.assertFalse(formatted.contains("\u200B"));
    Assertions.assertTrue(formatted.contains("Time:"));
  }

  private static List<String> hourlyValues(String value) {
    return java.util.Collections.nCopies(24, value);
  }

  private static void setField(Weather weather, String fieldName, Object value) {
    try {
      var field = Weather.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(weather, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
