package org.saturn.app.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

public class Util {
    static Gson gson = new Gson();
    
    public static String getAuthor(String author) {
        return author.replace("@", "");
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
    
    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
