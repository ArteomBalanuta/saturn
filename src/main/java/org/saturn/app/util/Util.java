package org.saturn.app.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.User;

import java.util.*;
import java.util.stream.Collectors;

public class Util {
    public static Gson gson = new Gson();
    
    public static String getAuthor(String author) {
        return author == null ? null : author.replace("@", "");
    }
    
    public static String extractCmdFromJson(String jsonText) {
        JsonElement element = JsonParser.parseString(jsonText);
        JsonElement listingElement = element.getAsJsonObject().get("cmd");
        return gson.fromJson(listingElement, String.class);
    }
    
    public static List setToList(Set set) {
        return new ArrayList(set);
    }
    public static List<String> toLower(List<String> l) {
        return l.stream().map(String::toLowerCase).collect(Collectors.toList());
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

    public static List<User> extractUsersFromJson(String jsonText){
        JsonElement e = JsonParser.parseString(jsonText);
        JsonElement listingElement = e.getAsJsonObject().get("users");
        User[] users = gson.fromJson(listingElement, User[].class);
        return Arrays.asList(users);
    }
}
