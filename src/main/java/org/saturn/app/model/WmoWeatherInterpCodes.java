package org.saturn.app.model;

public enum WmoWeatherInterpCodes {
  CLEAR_SKY(new WeatherCode(0, "\uD83C\uDF24\uFE0F")),
  MAINLY_CLEAR(new WeatherCode(1, "\uD83C\uDF24\uFE0F")),
  PARTLY_CLOUDY(new WeatherCode(2, "\uD83C\uDF25\uFE0F")),
  OVERCAST(new WeatherCode(3, "☁\uFE0F")),
  FOG(new WeatherCode(45, "\uD83C\uDF2B\uFE0F")),
  DEPOSITING_RIME_FOG(new WeatherCode(48, "\uD83D\uDE36\u200D\uD83C\uDF2B\uFE0F")),
  DRIZZLE_LIGHT(new WeatherCode(51, "\uD83C\uDF26\uFE0F")),
  DRIZZLE_MODERATE(new WeatherCode(53, "\uD83C\uDF27\uFE0F")),
  DRIZZLE_DENSE(new WeatherCode(55, "\uD83C\uDF27\uFE0F")),
  FREEZING_DRIZZLE_LIGHT(new WeatherCode(56, "\uD83C\uDF27\uFE0F")),
  FREEZING_DRIZZLE_DENSE(new WeatherCode(57, "\uD83C\uDF27\uFE0F")),
  RAIN_SLIGHT(new WeatherCode(61, "\uD83C\uDF26\uFE0F")),
  RAIN_MODERATE(new WeatherCode(63, "\uD83C\uDF27\uFE0F")),
  RAIN_HEAVY(new WeatherCode(65, "\uD83C\uDF27\uFE0F")),
  FREEZING_RAIN_LIGHT(new WeatherCode(66, "\uD83C\uDF27\uFE0F")),
  FREEZING_RAIN_HEAVY(new WeatherCode(67, "\uD83C\uDF27\uFE0F")),
  SNOW_FALL_SLIGHT(new WeatherCode(71, "❄\uFE0F")),
  SNOW_FALL_MODERATE(new WeatherCode(73, "❄\uFE0F")),
  SNOW_FALL_HEAVY(new WeatherCode(75, "❄\uFE0F")),
  SNOW_GRAINS(new WeatherCode(77, "❄\uFE0F")),
  RAIN_SHOWERS_SLIGHT(new WeatherCode(80, "\uD83D\uDEBF")),
  RAIN_SHOWERS_MODERATE(new WeatherCode(81, "\uD83D\uDEBF")),
  RAIN_SHOWERS_VIOLENT(new WeatherCode(82, "\uD83D\uDEBF")),
  SNOW_SHOWERS_SLIGHT(new WeatherCode(85, "\uD83D\uDEBF")),
  SNOW_SHOWERS_HEAVY(new WeatherCode(86, "❄\uFE0F")),
  THUNDERSTORM_SLIGHT_MODERATE(new WeatherCode(95, "⛈\uFE0F")),
  THUNDERSTORM_WITH_HAIL_SLIGHT(new WeatherCode(96, "⛈\uFE0F")),
  THUNDERSTORM_WITH_HAIL_HEAVY(new WeatherCode(99, "⛈\uFE0F"));

  public static class WeatherCode {
    int code;
    String weatherEmoji;

    public WeatherCode(int code, String weatherEmoji) {
      this.code = code;
      this.weatherEmoji = weatherEmoji;
    }

    @Override
    public String toString() {
      return String.valueOf(code);
    }

    public int getCode() {
      return code;
    }

    public void setCode(int code) {
      this.code = code;
    }

    public String getWeatherEmoji() {
      return weatherEmoji;
    }

    public void setWeatherEmoji(String weatherEmoji) {
      this.weatherEmoji = weatherEmoji;
    }
  }

  private final WeatherCode value;

  WmoWeatherInterpCodes(WeatherCode value) {
    this.value = value;
  }

  public WeatherCode getValue() {
    return value;
  }
}
