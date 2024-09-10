package org.saturn.app.model.dto;


import com.google.gson.Gson;

import java.util.List;

import static org.saturn.app.util.Util.gson;

public class Weather {
    
    private String latitude;
    private String longitude;
    private String timezone;
    private String elevation;

    private CurrentWeatherUnits current_weather_units;
    private CurrentWeather current_weather;
    private DailyUnits daily_units;
    private Daily daily;
    private HourlyUnits hourly_units;
    private Hourly hourly;

    public CurrentWeatherUnits getCurrent_weather_units() {
        return current_weather_units;
    }

    public void setCurrent_weather_units(CurrentWeatherUnits current_weather_units) {
        this.current_weather_units = current_weather_units;
    }

    public HourlyUnits getHourly_units() {
        return hourly_units;
    }

    public void setHourly_units(HourlyUnits hourly_units) {
        this.hourly_units = hourly_units;
    }

    public Hourly getHourly() {
        return hourly;
    }

    public void setHourly(Hourly hourly) {
        this.hourly = hourly;
    }

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
                ", hourly=" + hourly +
                '}';
    }
    
    public static Weather getWeather(String json) {
        return gson.fromJson(json, Weather.class);
    }
    
    public static WeatherTime getTime(String json) {
        return gson.fromJson(json, WeatherTime.class);
    }

    public static class CurrentWeatherUnits {
       public String time,
                interval,
                temperature,
                windspeed,
                winddirection,
                is_day,
                weathercode;

        @Override
        public String toString() {
            return "CurrentWeatherUnits{" +
                    "time='" + time + '\'' +
                    ", interval='" + interval + '\'' +
                    ", temperature='" + temperature + '\'' +
                    ", windspeed='" + windspeed + '\'' +
                    ", winddirection='" + winddirection + '\'' +
                    ", is_day='" + is_day + '\'' +
                    ", weathercode='" + weathercode + '\'' +
                    '}';
        }
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

    public static class Hourly {
        public List<String> apparent_temperature, pressure_msl, surface_pressure, shortwave_radiation, diffuse_radiation, visibility, soil_temperature_18cm, soil_moisture_3_to_9cm;

        @Override
        public String toString() {
            return "Hourly{" +
                    "apparent_temperature=" + apparent_temperature +
                    ", pressure_msl=" + pressure_msl +
                    ", surface_pressure=" + surface_pressure +
                    ", shortwave_radiation=" + shortwave_radiation +
                    ", diffuse_radiation=" + diffuse_radiation +
                    ", visibility=" + visibility +
                    ", soil_temperature_18cm=" + soil_temperature_18cm +
                    ", soil_moisture_3_to_9cm=" + soil_moisture_3_to_9cm +
                    '}';
        }
    }

    public static class HourlyUnits {
        public String
        pressure_msl,
        surface_pressure,
        soil_temperature_18cm,
        soil_moisture_3_to_9cm,
        visibility,
        diffuse_radiation,
        shortwave_radiation,
        apparent_temperature;

        @Override
        public String toString() {
            return "HourlyUnits{" +
                    ", pressure_msl=" + pressure_msl +
                    ", surface_pressure=" + surface_pressure +
                    ", soil_temperature_18cm=" + soil_temperature_18cm +
                    ", soil_moisture_3_to_9cm=" + soil_moisture_3_to_9cm +
                    ", visibility=" + visibility +
                    ", diffuse_radiation=" + diffuse_radiation +
                    ", shortwave_radiation=" + shortwave_radiation +
                    ", apparent_temperature=" + apparent_temperature +
                    '}';
        }
    }
}
