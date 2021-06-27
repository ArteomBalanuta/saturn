package org.saturn.app.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class Util {
    static Gson gson = new Gson();

    public static String getCmdFromJson(String jsonText) {
        JsonElement element = new JsonParser().parse(jsonText); //parse to json tree
        JsonElement listingElement = element.getAsJsonObject().get("cmd"); // extract key
        String cmd = gson.fromJson(listingElement, String.class);

        return cmd;
    }


}
