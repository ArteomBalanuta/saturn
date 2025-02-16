package org.saturn.app.util;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class DateUtil {

  /* mills */
  public static long getTimestampNow() {
    return Timestamp.from(Instant.now()).getTime();
  }

  public static String getUtcNow() {
    return OffsetDateTime.now(ZoneOffset.UTC).toString();
  }

  public static Long tsToSec8601(String timestamp, String zoneId) {
    if (timestamp == null) return null;
    try {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
      sdf.setTimeZone(TimeZone.getTimeZone(ZoneId.of(Objects.requireNonNullElse(zoneId, "UTC"))));

      Date dt = sdf.parse(timestamp);
      return dt.getTime() / 1000;
    } catch (ParseException e) {
      return null;
    }
  }

  public static String getDifference(ZonedDateTime first, ZonedDateTime second) {
    Duration duration = Duration.between(second, first);

    // Extract days, hours, minutes, and seconds
    long days = duration.toDays();
    long hours = duration.minusDays(days).toHours();
    long minutes = duration.minusDays(days).minusHours(hours).toMinutes();
    long seconds = duration.minusDays(days).minusHours(hours).minusMinutes(minutes).getSeconds();

    return days + " days, " + hours + " hours, " + minutes + " minutes, " + seconds + " seconds";
  }

  public static String formatZoneUTC(long timestamp) {
    ZonedDateTime zonedDateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of("UTC"));

    return formatTime(zonedDateTime);
  }

  public static ZonedDateTime toZoneDateTimeUTC(long timestamp) {
    return Instant.ofEpochMilli(timestamp).atZone(ZoneId.of("UTC"));
  }

  public static String formatTime(ZonedDateTime zonedDateTime) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss z");
    return zonedDateTime.format(formatter);
  }

  public static String formatRfc1123(long epochTimestamp, TimeUnit timeUnit, String zoneId) {
    ZonedDateTime zonedDateTime;
    switch (timeUnit) {
      case SECONDS ->
          zonedDateTime = Instant.ofEpochSecond(epochTimestamp).atZone(ZoneId.of(zoneId));
      case MILLISECONDS ->
          zonedDateTime = Instant.ofEpochMilli(epochTimestamp).atZone(ZoneId.of(zoneId));
      default -> throw new RuntimeException("Timestamp should be in seconds or milliseconds.");
    }

    return zonedDateTime.format(RFC_1123_DATE_TIME);
  }
}
