package org.saturn.app.util;

public class Util {
    public static String getCmdFromJson(String jsonText) {
        return jsonText.substring(jsonText.indexOf("cmd\":\"") + 6, jsonText.indexOf("\","));
    }
}
