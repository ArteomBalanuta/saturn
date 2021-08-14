package org.saturn.app.util;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class Util {
    static Gson gson = new Gson();
    
    public static String getCmdFromJson(String jsonText) {
        JsonElement element = new JsonParser().parse(jsonText);
        JsonElement listingElement = element.getAsJsonObject().get("cmd");
        String cmd = gson.fromJson(listingElement, String.class);

        return cmd;
    }

    public static long getTimestampNow() {
        return Timestamp.valueOf(LocalDateTime.now()).getTime();
    }
}
