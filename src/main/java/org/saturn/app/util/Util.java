package org.saturn.app.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.saturn.app.facade.impl.EngineImpl;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

public class Util {
    public static Gson gson = new Gson();
    
    public static String getAuthor(String author) {
        return author == null ? null : author.replace("@", "");
    }
    
    public static String getCmdFromJson(String jsonText) {
        System.out.println(jsonText);
        JsonElement element = JsonParser.parseString(jsonText);
        JsonElement listingElement = element.getAsJsonObject().get("cmd");
        return gson.fromJson(listingElement, String.class);
    }
    
    public static long getTimestampNow() {
        return Timestamp.from(Instant.now()).getTime();
    }
    
    public static String getUTCnow() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
    
    public static Long tsToSec8601(String timestamp) {
        if (timestamp == null) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
            sdf.setTimeZone(TimeZone.getTimeZone(ZoneId.of("UTC")));
            Date dt = sdf.parse(timestamp);
            return dt.getTime() / 1000;
        } catch (ParseException e) {
            return null;
        }
    }
    public static String formatZoneUTC(long timestamp) {
        ZonedDateTime zonedDateTime = Instant.ofEpochSecond(timestamp)
                .atZone(ZoneId.of("UTC"));

        return formatTime(zonedDateTime);
    }
    public static String formatTime(ZonedDateTime zonedDateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss z");
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
    
    public static boolean is(String cmd, Cmd enumCmd) {
        String validCmd = enumCmd.getCmdCode();
        
        if (cmd.length() < validCmd.length()) {
            return false;
        }
        
        // checking if the string starts with expected cmd
        for (int i = 0; i < validCmd.length(); i++) {
            if (validCmd.charAt(i) == cmd.charAt(i)) {
                continue;
            }
            
            return false;
        }
        
        return true;
    }
    
    public static List setToList(Set set) {
        List list = new ArrayList();
        list.addAll(set);
        return list;
    }
    public static List<String> toLower(List<String> l) {
        return l.stream().map(String::toLowerCase).collect(Collectors.toList());
    }
    
    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static List<String> getWhiteListedTrips(EngineImpl engine) {
        List<String> whiteListedTrips = new ArrayList<>();
        whiteListedTrips.addAll(Arrays.asList(engine.adminTrips.split(",")));
        whiteListedTrips.addAll(Arrays.asList(engine.userTrips.split(",")));
        return whiteListedTrips;
    }

    public static List<String> getAdminTrips(EngineImpl engine) {
        return new ArrayList<>(Arrays.asList(engine.adminTrips.split(",")));
    }
}
