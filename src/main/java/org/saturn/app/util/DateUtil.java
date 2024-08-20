package org.saturn.app.util;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

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

        // Display the output
        return  "was Afk for " + days + " days, " + hours + " hours, " + minutes + " minutes, " + seconds + " seconds";
    }

    public static String formatZoneUTC(long timestamp) {
        ZonedDateTime zonedDateTime = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.of("UTC"));

        return formatTime(zonedDateTime);
    }

    public static String formatTime(ZonedDateTime zonedDateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss z");
        return zonedDateTime.format(formatter);
    }

    public static String formatZone(long timestamp, String zoneId) {
        ZonedDateTime zonedDateTime = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.of(zoneId)); //"UTC"

        return formatZoneDateTime(zonedDateTime);
    }

    public static String formatZoneDateTime(ZonedDateTime zonedDateTime) {
        return zonedDateTime.format(RFC_1123_DATE_TIME);
    }
}
