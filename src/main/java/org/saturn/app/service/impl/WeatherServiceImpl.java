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
import java.util.concurrent.atomic.AtomicReference;

import static org.saturn.app.model.dto.Weather.getTime;
import static org.saturn.app.util.Util.formatTime;
import static org.saturn.app.util.Util.tsToSec8601;

public class WeatherServiceImpl extends OutService implements WeatherService {
    private final Calendar calendar = Calendar.getInstance();
    
    private final DateTimeFormatter dateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
    
    /*
    http://api.geonames.org/search?q=london&maxRows=1&username=dev1
    https://api.open-meteo.com/v1/forecast?
    username: mercury389
     */
    @Override
    public void executeWeather(String owner, List<String> arguments) {
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
        
        /*
         **Current weather data for:** Perth\n\n>Temperature: 299K / 26°C / 78°F \nAtmospheric Pressure (hPa):
         * 1011\nHumidity (%): 61\nDescription: clear sky
         */
        Weather.Daily daily = weather.getDaily();
        Weather.DailyUnits dailyUnits = weather.getDaily_units();
        Weather.Hourly hourly = weather.getHourly();
        Weather.CurrentWeather currentWeather = weather.getCurrent_weather();
        
        String timeZoneUri = String.format("https://timeapi.io/api/Time/current/coordinate?latitude=%s&longitude=%s", lat, lng);
//        String timeZoneResponse = getResponseByURL(timeZoneUri);
//        List<String> timeZones = Arrays.asList(timeZoneResponse.replace("[\"", "").replace("\"]", "").split("\",\""));
//        Optional<String> timeZone = timeZones.stream().filter(z -> z.toLowerCase().contains(zone)).findFirst();
//        Time time = null;
//        if (timeZone.isPresent()) {
//

          Time time = getTime(getResponseByURL(timeZoneUri));
//        }
        
        String message = formatWeather(zone + " ," + country, daily, currentWeather, dailyUnits, time, hourly);
        
        enqueueMessageForSending(message);
    }
    
    private String formatWeather(String area, Weather.Daily daily, Weather.CurrentWeather currentWeather,
                                 Weather.DailyUnits dailyUnits, Time time, Weather.Hourly hourly) {

        ZonedDateTime zonedDateTime = Instant.ofEpochSecond(tsToSec8601(time.dateTime, time.timeZone)).atZone(ZoneId.of(time.timeZone));

        ZonedDateTime sunriseDateTime = Instant.ofEpochSecond(tsToSec8601(daily.sunrise.get(0), null))
                .atZone(ZoneId.of(time.timeZone));
        ZonedDateTime sunsetDateTime = Instant.ofEpochSecond(tsToSec8601(daily.sunset.get(0), null))
                .atZone(ZoneId.of(time.timeZone));
    
        String currentTime = formatTime(zonedDateTime);
        String sunriseTime = formatTime(sunriseDateTime);
        String sunsetTime = formatTime(sunsetDateTime);
    
        return "Weather forecast for today: **" + area + "**\\n\\n" +
                ">Temperature: " + currentWeather.temperature + " " + dailyUnits.temperature_2m_max + "\\n" +
                ">Feels temp: " + hourly.apparent_temperature.get(zonedDateTime.getHour()) + "\\n" +
                ">Wind speed  : " + currentWeather.windspeed + " m/s" + "\\n" +
                ">Pressure surface: " + hourly.surface_pressure.get(zonedDateTime.getHour()) + "\\n" +
                ">Pressure sea level: " + hourly.pressure_msl.get(zonedDateTime.getHour()) + " \\r\\n " +
                "\u200B\u200B\u200B \\r\\n" +
                "UV day max index    : " + daily.uv_index_max.get(0) + "\\n" +
                "Short wave radiation day sum: " + daily.shortwave_radiation_sum.get(0) + "\\n" +
                "ShortWave rad: " + hourly.shortwave_radiation.get(zonedDateTime.getHour()) + "\\n" +
                "Diffuse rad: " + hourly.diffuse_radiation.get(zonedDateTime.getHour()) + "\\n" +
                "\u200B\u200B\u200B \\n" +
                "Time        : " + currentTime + "\\n" +
                "Sun rise    : " + sunriseTime + "\\n" +
                "Sun set     : " + sunsetTime + "\\n" +
                "\u200B\u200B\u200B \\n" +
                "Soil temp 18cm: " + hourly.soil_temperature_18cm.get(zonedDateTime.getHour()) + "\\n" +
                "Soil moist 3-9cm: " + hourly.soil_moisture_3_to_9cm.get(zonedDateTime.getHour()) + "\\n";
    }
    

    
    private String epochSecondsToTime(long millis) {
        return String.format("%tT", millis * 1000);
    }
    
    private String extractCoordinates(String body) {
        String lat = StringUtils.substringBetween(body, "<lat>", "</lat>");
        String lng = StringUtils.substringBetween(body, "<lng>", "</lng>");
        
        return lat + "," + lng;
    }

    private String extractCountryName(String body) {
        return StringUtils.substringBetween(body, "<countryName>","</countryName>");
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
                result = "Classified :3";
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public WeatherServiceImpl(BlockingQueue<String> queue) {
        super(queue);
    }
}


