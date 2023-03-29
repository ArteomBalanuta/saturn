package org.saturn.app.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.dto.Time;
import org.saturn.app.model.dto.Weather;
import org.saturn.app.service.WeatherService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
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
    public void executeWeather(String owner, UserCommand userCommand) {
        AtomicReference<String> area = new AtomicReference<>();
        userCommand.getArguments().stream()
                .findFirst()
                .ifPresent(area::set);
        
        String zone = area.toString().trim();
        
        String uri = String.format("http://api.geonames.org/search?q=%s&maxRows=1&username=dev1", zone);
        String coordinates = extractCoordinates(getResponseByURL(uri));
        
        String lat = coordinates.split(",")[0];
        String lng = coordinates.split(",")[1];
        
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String curr_date = simpleDateFormat.format(calendar.getTime());
        
        String weatherApi = String.format("https://api.open-meteo.com/v1/forecast?" +
                "latitude=%s" +
                "&longitude=%s" +
                "&current_weather=true" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset," +
                "winddirection_10m_dominant,shortwave_radiation_sum,uv_index_max,uv_index_clear_sky_max" +
                "&timezone=GMT" +
                "&start_date=%s" +
                "&end_date=%s", lat, lng, curr_date, curr_date);
        
        String response = getResponseByURL(weatherApi);
        
        Weather weather = Weather.getWeather(response.trim());
        
        /*
         **Current weather data for:** Perth\n\n>Temperature: 299K / 26°C / 78°F \nAtmospheric Pressure (hPa):
         * 1011\nHumidity (%): 61\nDescription: clear sky
         */
        Weather.Daily daily = weather.getDaily();
        Weather.DailyUnits dailyUnits = weather.getDaily_units();
        Weather.CurrentWeather currentWeather = weather.getCurrent_weather();
        
        String timeZoneUri = String.format("https://timeapi.io/api/Time/current/coordinate?latitude=%s&longitude=%s", lat, lng);
        String timeZoneResponse = getResponseByURL(timeZoneUri);
        List<String> timeZones = Arrays.asList(timeZoneResponse.replace("[\"", "").replace("\"]", "").split("\",\""));
        Optional<String> timeZone = timeZones.stream().filter(z -> z.contains(zone)).findFirst();
        Time time = null;
        if (timeZone.isPresent()) {
            time = getTime(getResponseByURL(timeZoneUri.concat(timeZone.get())));
        }
        
        String message = formatWeather(area.get(), daily, currentWeather, dailyUnits, time);
        
        enqueueMessageForSending(message);
    }
    
    private String formatWeather(String area, Weather.Daily daily, Weather.CurrentWeather currentWeather,
                                 Weather.DailyUnits dailyUnits, Time time) {
        
        ZonedDateTime zonedDateTime = Instant.ofEpochSecond(tsToSec8601(time.utc_datetime))
                .atZone(ZoneId.of(time.timezone));
        ZonedDateTime sunriseDateTime = Instant.ofEpochSecond(tsToSec8601(daily.sunrise.get(0)))
                .atZone(ZoneId.of(time.timezone));
        ZonedDateTime sunsetDateTime = Instant.ofEpochSecond(tsToSec8601(daily.sunset.get(0)))
                .atZone(ZoneId.of(time.timezone));
    
        String currentTime = formatTime(zonedDateTime);
        String sunriseTime = formatTime(sunriseDateTime);
        String sunsetTime = formatTime(sunsetDateTime);
    
        return "Weather forecast for today: **" + area + "**\\n\\n" +
                ">Temperature: " + currentWeather.temperature + " " + dailyUnits.temperature_2m_max + "\\n" +
                "Wind speed  : " + currentWeather.windspeed + " m/s" + "\\n" +
                "UV index    : " + daily.uv_index_max.get(0) + "\\n" +
                "Short wave radiation sum: " + daily.shortwave_radiation_sum.get(0) + "\\n" +
                " \\n" +
                "Time        : " + currentTime + "\\n" +
                "Sun rise    : " + sunriseTime + " \\n" +
                "Sun set     : " + sunsetTime;
    }
    

    
    private String epochSecondsToTime(long millis) {
        return String.format("%tT", millis * 1000);
    }
    
    private String extractCoordinates(String body) {
        String lat = StringUtils.substringBetween(body, "<lat>", "</lat>");
        String lng = StringUtils.substringBetween(body, "<lng>", "</lng>");
        
        return lat + "," + lng;
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


