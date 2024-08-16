package org.saturn.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.model.WmoWeatherInterpCodes;
import org.saturn.app.model.dto.Weather;
import org.saturn.app.service.WeatherService;
import org.saturn.app.util.Util;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.saturn.app.util.DateUtil.formatRfc1123;
import static org.saturn.app.util.DateUtil.tsToSec8601;

@Slf4j
public class WeatherServiceImpl extends OutService implements WeatherService {
  public WeatherServiceImpl(BlockingQueue<String> queue) {
    super(queue);
  }

  private final String apiGeoNames = "http://api.geonames.org/search?q=%s&maxRows=1&username=dev1";
  private final Calendar calendar = Calendar.getInstance();

  /*
  http://api.geonames.org/search?q=london&maxRows=1&username=dev1
  https://api.open-meteo.com/v1/forecast?
  username: mercury389
   */
  @Override
  public String getWeather(List<String> arguments) {
    StringBuilder zoneB = new StringBuilder();
    arguments.forEach(a -> zoneB.append(" ").append(a));

    String zone = zoneB.toString().trim();

    String uri = String.format(apiGeoNames, zone.trim().replace(" ", "%20"));
    String body = Util.getResponseByURL(uri);
    String coordinates = Util.extractCoordinates(body);
    String country = Util.extractCountryName(body);

    String lat = coordinates.split(",")[0];
    String lng = coordinates.split(",")[1];

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    String curr_date = simpleDateFormat.format(calendar.getTime());

    String weatherApi =
        String.format(
            "https://api.open-meteo.com/v1/forecast?"
                + "latitude=%s"
                + "&longitude=%s"
                + "&current_weather=true"
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset,"
                + "winddirection_10m_dominant,shortwave_radiation_sum,uv_index_max,uv_index_clear_sky_max,weather_code"
                + "&hourly=pressure_msl,surface_pressure,soil_temperature_18cm,soil_moisture_3_to_9cm,visibility,diffuse_radiation,shortwave_radiation,apparent_temperature,relative_humidity_2m"
                + "&timezone=auto"
                + "&start_date=%s"
                + "&end_date=%s",
            lat, lng, curr_date, curr_date);

    log.debug("Getting: {}", weatherApi);

    String response = Util.getResponseByURL(weatherApi);

    if (response == null) {
      return null;
    }
    Weather weather = Weather.getWeather(response.trim());

    Weather.Daily daily = weather.getDaily();
    Weather.DailyUnits dailyUnits = weather.getDaily_units();
    Weather.Hourly hourly = weather.getHourly();
    Weather.HourlyUnits hourlyUnits = weather.getHourly_units();
    Weather.CurrentWeather currentWeather = weather.getCurrent_weather();
    Weather.CurrentWeatherUnits currentWeatherUnits = weather.getCurrent_weather_units();
    return formatWeather(
        zone + ", " + country,
        daily,
        currentWeather,
        dailyUnits,
        weather,
        hourly,
        hourlyUnits,
        currentWeatherUnits);
  }

  public String formatWeather(
      String area,
      Weather.Daily daily,
      Weather.CurrentWeather currentWeather,
      Weather.DailyUnits dailyUnits,
      Weather weather,
      Weather.Hourly hourly,
      Weather.HourlyUnits hourlyUnits,
      Weather.CurrentWeatherUnits currentWeatherUnits) {

    String time = currentWeather.time;
    String timeZone = weather.getTimezone();
    ZonedDateTime zonedDateTime =
        Instant.ofEpochSecond(tsToSec8601(time, timeZone))
            .atZone(ZoneId.of(timeZone));
    ZonedDateTime sunriseDateTime =
        Instant.ofEpochSecond(tsToSec8601(daily.sunrise.get(0), null))
            .atZone(ZoneId.of(timeZone));
    ZonedDateTime sunsetDateTime =
        Instant.ofEpochSecond(tsToSec8601(daily.sunset.get(0), null))
            .atZone(ZoneId.of(timeZone));

    String currentTime =
        formatRfc1123(
            zonedDateTime.toEpochSecond(), TimeUnit.SECONDS, zonedDateTime.getZone().toString());
    String sunriseTime =
        formatRfc1123(
            sunriseDateTime.toEpochSecond(), TimeUnit.SECONDS, zonedDateTime.getZone().toString());
    String sunsetTime =
        formatRfc1123(
            sunsetDateTime.toEpochSecond(), TimeUnit.SECONDS, zonedDateTime.getZone().toString());

    String weatherEmoji =
        Arrays.stream(WmoWeatherInterpCodes.values())
            .filter(v -> currentWeather.weathercode.equals(String.valueOf(v.getValue().getCode())))
            .findFirst()
            .get()
            .getValue()
            .getWeatherEmoji();

    log.warn("Using weather emoji: {}", weatherEmoji);
    return "Weather forecast for today: **"
        + area
        + "**\\n"
        + "Temperature: "
        + currentWeather.temperature
        + " "
        + currentWeatherUnits.temperature
        + "\\n"
        + "Feels temp: "
        + hourly.apparent_temperature.get(zonedDateTime.getHour())
        + " "
        + hourlyUnits.apparent_temperature
        + "\\n"
        + "Air Humidity: "
        + hourly.relative_humidity_2m.get(zonedDateTime.getHour())
        + " "
        + hourlyUnits.relative_humidity_2m
        + "\\n"
        + "Precipitation: "
        + weatherEmoji
        + "\\n"
        + "Wind speed: "
        + currentWeather.windspeed
        + " "
        + currentWeatherUnits.windspeed
        + "\\n"
        + "Pressure surface: "
        + hourly.surface_pressure.get(zonedDateTime.getHour())
        + " "
        + hourlyUnits.surface_pressure
        + "\\n"
        + "Pressure sea level: "
        + hourly.pressure_msl.get(zonedDateTime.getHour())
        + " "
        + hourlyUnits.pressure_msl
        + "\\n"
        + "\u200B\u200B\u200B \\n"
        + "UV day max index: "
        + daily.uv_index_max.get(0)
        + " "
        + dailyUnits.uv_index_max
        + "\\n"
        + "Short wave radiation day sum: "
        + daily.shortwave_radiation_sum.get(0)
        + " "
        + dailyUnits.shortwave_radiation_sum
        + "\\n"
        + "ShortWave rad: "
        + hourly.shortwave_radiation.get(zonedDateTime.getHour())
        + " "
        + hourlyUnits.shortwave_radiation
        + "\\n"
        + "Diffuse rad: "
        + hourly.diffuse_radiation.get(zonedDateTime.getHour())
        + " "
        + hourlyUnits.diffuse_radiation
        + "\\n"
        + "\u200B\u200B\u200B \\n"
        + "Time: "
        + currentTime.replace(":", "-")
        + "\\n"
        + "Sun rise: "
        + sunriseTime.replace(":", "-")
        + "\\n"
        + "Sun set: "
        + sunsetTime.replace(":", "-")
        + "\\n"
        + "\u200B\u200B\u200B \\n"
        + "Soil temp 18cm: "
        + hourly.soil_temperature_18cm.get(zonedDateTime.getHour())
        + " "
        + hourlyUnits.soil_temperature_18cm
        + "\\n"
        + "Soil moist 3-9cm: "
        + hourly.soil_moisture_3_to_9cm.get(zonedDateTime.getHour())
        + " "
        + hourlyUnits.soil_moisture_3_to_9cm
        + "\\n";
  }
}
