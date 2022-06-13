package org.saturn.app.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
}
