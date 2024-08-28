package org.saturn.app.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.saturn.app.model.dto.Time;
import org.saturn.app.model.dto.Weather;
import org.saturn.app.service.WeatherService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.saturn.app.model.dto.Weather.getTime;
import static org.saturn.app.util.DateUtil.formatRfc1123;
import static org.saturn.app.util.DateUtil.formatTime;
import static org.saturn.app.util.DateUtil.tsToSec8601;

public class WeatherServiceImpl extends OutService implements WeatherService {
    public WeatherServiceImpl(BlockingQueue<String> queue) {
        super(queue);
    }

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

        String uri = String.format("http://api.geonames.org/search?q=%s&maxRows=1&username=dev1", zone.trim().replace(" ", "%20"));
        String body = getResponseByURL(uri);
        String coordinates = extractCoordinates(body);
        String country = extractCountryName(body);

        String lat = coordinates.split(",")[0];
        String lng = coordinates.split(",")[1];

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String curr_date = simpleDateFormat.format(calendar.getTime());

        String weatherApi = String.format("https://api.open-meteo.com/v1/forecast?" +
                        "latitude=%s" +
                        "&longitude=%s" +
                        "&current_weather=true" +
                        "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset," +
                        "winddirection_10m_dominant,shortwave_radiation_sum,uv_index_max,uv_index_clear_sky_max,weather_code" +
                        "&hourly=pressure_msl,surface_pressure,soil_temperature_18cm,soil_moisture_3_to_9cm,visibility,diffuse_radiation,shortwave_radiation,apparent_temperature" +
                        "&timezone=GMT" +
                        "&start_date=%s" +
                        "&end_date=%s"
                , lat, lng, curr_date, curr_date);

        System.out.println("Curling: " + weatherApi);

        String response = getResponseByURL(weatherApi);

        Weather weather = Weather.getWeather(response.trim());

        Weather.Daily daily = weather.getDaily();
        Weather.DailyUnits dailyUnits = weather.getDaily_units();
        Weather.Hourly hourly = weather.getHourly();
        Weather.HourlyUnits hourlyUnits = weather.getHourly_units();
        Weather.CurrentWeather currentWeather = weather.getCurrent_weather();
        Weather.CurrentWeatherUnits currentWeatherUnits = weather.getCurrent_weather_units();

        String timeZoneUri = String.format("https://timeapi.io/api/Time/current/coordinate?latitude=%s&longitude=%s", lat, lng);
        Time time = getTime(getResponseByURL(timeZoneUri));

        return formatWeather(zone + ", " + country, daily, currentWeather, dailyUnits, time, hourly, hourlyUnits, currentWeatherUnits);
    }

    private String formatWeather(String area, Weather.Daily daily, Weather.CurrentWeather currentWeather,
                                 Weather.DailyUnits dailyUnits, Time time, Weather.Hourly hourly, Weather.HourlyUnits hourlyUnits, Weather.CurrentWeatherUnits currentWeatherUnits) {

        ZonedDateTime zonedDateTime = Instant.ofEpochSecond(tsToSec8601(time.dateTime, time.timeZone)).atZone(ZoneId.of(time.timeZone));
        ZonedDateTime sunriseDateTime = Instant.ofEpochSecond(tsToSec8601(daily.sunrise.get(0), null)).atZone(ZoneId.of(time.timeZone));
        ZonedDateTime sunsetDateTime = Instant.ofEpochSecond(tsToSec8601(daily.sunset.get(0), null)).atZone(ZoneId.of(time.timeZone));

        String currentTime = formatRfc1123(zonedDateTime.toEpochSecond(), TimeUnit.SECONDS, zonedDateTime.getZone().toString());
        String sunriseTime = formatRfc1123(sunriseDateTime.toEpochSecond(), TimeUnit.SECONDS, zonedDateTime.getZone().toString());
        String sunsetTime = formatRfc1123(sunsetDateTime.toEpochSecond(), TimeUnit.SECONDS, zonedDateTime.getZone().toString());

        return "Weather forecast for today: **" + area + "**\\n" +
                "Temperature: " + currentWeather.temperature + " " + currentWeatherUnits.temperature + "\\n" +
                "Feels temp: " + hourly.apparent_temperature.get(zonedDateTime.getHour()) + " " + hourlyUnits.apparent_temperature + "\\n" +
                "Wind speed: " + currentWeather.windspeed + " " + currentWeatherUnits.windspeed + "\\n" +
                "Pressure surface: " + hourly.surface_pressure.get(zonedDateTime.getHour()) + " " + hourlyUnits.surface_pressure + "\\n" +
                "Pressure sea level: " + hourly.pressure_msl.get(zonedDateTime.getHour()) + " " + hourlyUnits.pressure_msl + "\\n" +
                "\u200B\u200B\u200B \\n" +
                "UV day max index: " + daily.uv_index_max.get(0) + " " + dailyUnits.uv_index_max + "\\n" +
                "Short wave radiation day sum: " + daily.shortwave_radiation_sum.get(0) + " " + dailyUnits.shortwave_radiation_sum + "\\n" +
                "ShortWave rad: " + hourly.shortwave_radiation.get(zonedDateTime.getHour()) + " " + hourlyUnits.shortwave_radiation + "\\n" +
                "Diffuse rad: " + hourly.diffuse_radiation.get(zonedDateTime.getHour()) + " " + hourlyUnits.diffuse_radiation + "\\n" +
                "\u200B\u200B\u200B \\n" +
                "Time: " + currentTime.replace(":","-") + "\\n" +
                "Sun rise: " + sunriseTime.replace(":","-") + "\\n" +
                "Sun set: " + sunsetTime.replace(":","-") + "\\n" +
                "\u200B\u200B\u200B \\n" +
                "Soil temp 18cm: " + hourly.soil_temperature_18cm.get(zonedDateTime.getHour()) + " " + hourlyUnits.soil_temperature_18cm + "\\n" +
                "Soil moist 3-9cm: " + hourly.soil_moisture_3_to_9cm.get(zonedDateTime.getHour()) + " " + hourlyUnits.soil_moisture_3_to_9cm + "\\n";
    }

    private String extractCoordinates(String body) {
        String lat = StringUtils.substringBetween(body, "<lat>", "</lat>");
        String lng = StringUtils.substringBetween(body, "<lng>", "</lng>");

        return lat + "," + lng;
    }

    private String extractCountryName(String body) {
        return StringUtils.substringBetween(body, "<countryName>", "</countryName>");
    }

    private String getResponseByURL(String uri) {
        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpGet request = new HttpGet(uri);

        // add request headers
        request.addHeader(HttpHeaders.USER_AGENT, "Firefox 59.9.0-MDA-Universe");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            // Get HttpResponse Status
            System.out.println(response.getProtocolVersion());              // HTTP/1.1
            System.out.println(response.getStatusLine().getStatusCode());   // 200
            System.out.println(response.getStatusLine().getReasonPhrase()); // OK
            System.out.println(response.getStatusLine().toString());        // HTTP/1.1 200 OK

            String result = null;
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                // return it as a String
                result = EntityUtils.toString(entity);
            }
            if (response.getStatusLine().getStatusCode() != 200) {
                result = "API Response status code: " + response.getStatusLine().getStatusCode();
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}


