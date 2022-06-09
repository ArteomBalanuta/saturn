package org.saturn.app.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.sql.Timestamp;
import java.time.Instant;

public class Util {
    static Gson gson = new Gson();
    
    public static String getCmdFromJson(String jsonText) {
        JsonElement element = JsonParser.parseString(jsonText);
        JsonElement listingElement = element.getAsJsonObject().get("cmd");
        return gson.fromJson(listingElement, String.class);
    }

    public static long getTimestampNow() {
        return Timestamp.from(Instant.now()).getTime();
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
}
