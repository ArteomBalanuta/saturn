package org.saturn.app.model.dto;

import com.google.gson.Gson;

import java.util.List;

public class Weather {
    static Gson gson = new Gson();
    
    private String latitude;
    private String longitude;
    private String timezone;
    private String elevation;
    private CurrentWeather current_weather;
    private DailyUnits daily_units;
    private Daily daily;
    
    public Daily getDaily() {
        return daily;
    }
    
    public static Gson getGson() {
        return gson;
    }
    
    public String getLatitude() {
        return latitude;
    }
    
    public String getLongitude() {
        return longitude;
    }
    
    public String getTimezone() {
        return timezone;
    }
    
    public String getElevation() {
        return elevation;
    }
    
    public CurrentWeather getCurrent_weather() {
        return current_weather;
    }
    
    public DailyUnits getDaily_units() {
        return daily_units;
    }
    
    @Override
    public String toString() {
        return "Weather{" +
                "latitude='" + latitude + '\'' +
                ", longitude='" + longitude + '\'' +
                ", timezone='" + timezone + '\'' +
                ", elevation='" + elevation + '\'' +
                ", currentWeather=" + current_weather +
                ", dailyUnits=" + daily_units +
                ", daily=" + daily +
                '}';
    }
    
    public static Weather getWeather(String json) {
        return gson.fromJson(json, Weather.class);
    }
    
    public static Time getTime(String json) {
        return gson.fromJson(json, Time.class);
    }
    
    public static class CurrentWeather {
        public String temperature;
        public String windspeed;
        public String winddirection;
        public String weathercode;
        public String time;
        
        @Override
        public String toString() {
            return "CurrentWeather{" +
                    "temperature='" + temperature + '\'' +
                    ", windspeed='" + windspeed + '\'' +
                    ", winddirection='" + winddirection + '\'' +
                    ", weathercode='" + weathercode + '\'' +
                    ", time='" + time + '\'' +
                    '}';
        }
    }
    
    public static class DailyUnits {
        public String time;
        public String temperature_2m_max; //°C
        public String temperature_2m_min;
        public String precipitation_sum; //mm
        public String sunrise;
        public String sunset;
        public String winddirection_10m_dominant; //°
        public String shortwave_radiation_sum;    //MJ/m²
        public String uv_index_max;
        public String uv_index_clear_sky_max;
        
        @Override
        public String toString() {
            return "DailyUnits{" +
                    "time='" + time + '\'' +
                    ", temperature_2m_max='" + temperature_2m_max + '\'' +
                    ", temperature_2m_min='" + temperature_2m_min + '\'' +
                    ", precipitation_sum='" + precipitation_sum + '\'' +
                    ", sunrise='" + sunrise + '\'' +
                    ", sunset='" + sunset + '\'' +
                    ", winddirection_10m_dominant='" + winddirection_10m_dominant + '\'' +
                    ", shortwave_radiation_sum='" + shortwave_radiation_sum + '\'' +
                    ", uv_index_max='" + uv_index_max + '\'' +
                    ", uv_index_clear_sky_max='" + uv_index_clear_sky_max + '\'' +
                    '}';
        }
    }
    
    public static class Daily {
        public List<String> time, temperature_2m_max, temperature_2m_min, precipitation_sum, sunrise, sunset,
                winddirection_10m_dominant, shortwave_radiation_sum, uv_index_max, uv_index_clear_sky_max;
        
        @Override
        public String toString() {
            return "Daily{" +
                    "time=" + time +
                    ", temperature_2m_max=" + temperature_2m_max +
                    ", temperature_2m_min=" + temperature_2m_min +
                    ", precipitation_sum=" + precipitation_sum +
                    ", sunrise=" + sunrise +
                    ", sunset=" + sunset +
                    ", winddirection_10m_dominant=" + winddirection_10m_dominant +
                    ", shortwave_radiation_sum=" + shortwave_radiation_sum +
                    ", uv_index_max=" + uv_index_max +
                    ", uv_index_clear_sky_max=" + uv_index_clear_sky_max +
                    '}';
        }
    }
}
