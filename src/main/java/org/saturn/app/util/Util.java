package org.saturn.app.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
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

    public static List<User> extractUsersFromJson(String jsonText) {
        JsonElement e = JsonParser.parseString(jsonText);
        JsonElement listingElement = e.getAsJsonObject().get("users");
        User[] users = gson.fromJson(listingElement, User[].class);
        return Arrays.asList(users);
    }

    public static boolean checkAnagrams(String target, List<String> words) {
        for (String word : words) {
            if (areAnagrams(target, word)) {
                return true;
            }
        }
        return false;
    }

    // Function to check if two strings are anagrams
    public static boolean areAnagrams(String input, String validCmd) {
        // If lengths are not equal, they cannot be anagrams
        if (input.length() != validCmd.length()) {
            return false;
        }

        // Convert strings to char arrays
        char[] array1 = input.toCharArray();
        char[] array2 = validCmd.toCharArray();

        // Sort the char arrays
        Arrays.sort(array1);
        Arrays.sort(array2);

        // Compare sorted arrays
        return Arrays.equals(array1, array2);
    }

    public static int findLongestLine(List<String> lines) {
        int max = -1;
        String longestLine = null;
        for (String line : lines) {
            if (line.length() > max) {
                max = line.length();
                longestLine = line;
            }
        }
        return lines.indexOf(longestLine);
    }

    public static String alignToLine(String input, String line) {
        StringBuilder tmp = new StringBuilder(input);
//        String space = "\u200B"; // is not displayed properly in hack client v1.
        String space = "&nbsp;";
        int left = line.length() - tmp.length();
        while (left != 0) {
//            tmp.append(space);
            tmp.insert(0, space); // prepend
            left--;
        }

        return tmp.toString();
    }

    public static String alignWithWhiteSpace(String output) {
        String[] lines = StringUtils.split(output.replace("\\n","\n"), '\n');

        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();

        for (String line : lines) {
            if (line.contains(":")) {
                String[] pair = StringUtils.split(line, ":");
                String key = pair[0];
                String value = pair[1];

                keys.add(key);
                values.add(value);
            }
        }

        int index = findLongestLine(keys);
        String longestLine = keys.get(index);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String aligned = alignToLine(keys.get(i), longestLine);
            result.append(aligned).append(":").append(values.get(i)).append("\\n");
        }

        return result.toString();
    }
}
