package org.saturn.app.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
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

    public static List<String> setToList(Set<String> set) {
        return new ArrayList<>(set);
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

    public static boolean checkAnagrams(String target, List<String> cmdAliases) {
        for (String alias : cmdAliases) {
            if (areAnagrams(target, alias)) {
                log.info("Command is anagram for: {}, alias", alias);
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

    public static String alignToLine(String input, String line, String space, boolean prepend) {
        StringBuilder tmp = new StringBuilder(input);
//        String space = "\u200B"; // is not displayed properly in hack client v1.
        int left = line.length() - tmp.length();
        while (left != 0) {
            if (prepend) {
                tmp.insert(0, space); // prepend
            } else {
                tmp.append(space);
            }
            left--;
        }

        return tmp.toString();
    }

    public static String alignWithWhiteSpace(String output, String separator, String space, boolean prepend) {
        String[] lines = StringUtils.splitByWholeSeparator(output, "\\n");

        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();

        for (String line : lines) {
            if (line.contains(separator)) {
                String[] pair = StringUtils.split(line, separator);
                String key = pair[0];
                String value = "";
                if (pair.length > 1) {
                    value = pair[1];
                }

                keys.add(key);
                values.add(value);
            }
        }

        int index = findLongestLine(keys);
        String longestLine = keys.get(index);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String aligned = alignToLine(keys.get(i), longestLine, space, prepend);
            result.append(aligned).append(separator).append(values.get(i)).append("\\n");
        }

        log.debug("Util: returning aligned with white spaces output: \\n {}", result);
        return result.toString();
    }

    public static String listToString(List<String> strings) {
        StringBuilder b = new StringBuilder();
        strings.forEach(string -> b.append(string).append(" "));
        return b.toString();
    }

    public static String extractCoordinates(String body) {
        String lat = StringUtils.substringBetween(body, "<lat>", "</lat>");
        String lng = StringUtils.substringBetween(body, "<lng>", "</lng>");

        return lat + "," + lng;
    }

    public static String extractCountryName(String body) {
        return StringUtils.substringBetween(body, "<countryName>", "</countryName>");
    }

    public static String getResponseByURL(String uri) {
        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpGet request = new HttpGet(uri);

        // add request headers
        request.addHeader(HttpHeaders.USER_AGENT, "Firefox 59.9.0-MDA-Universe");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            log.debug("Protocol: {}, StatusCode: {}, ", response.getProtocolVersion(), response.getStatusLine().getStatusCode());              // HTTP/1.1

            String result = null;
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                result = EntityUtils.toString(entity);
            }
            if (response.getStatusLine().getStatusCode() != 200) {
                log.info("API Response status code: {}", response.getStatusLine().getStatusCode());
            }
            return result;
        } catch (IOException e) {
            log.info("Error: {}", e.getMessage());
            log.error("Stack trace: ", e);
            return null;
        }
    }

    public static String listToCommaString(List<String> stringList) {
        String replace = stringList.toString().replace("[", "");
        return replace.replace("]", "");
    }

    public static void sleep(int value, TimeUnit timeUnit) {
        CountDownLatch latch = new CountDownLatch(1);
        // Wait for 5 seconds (doing nothing)
        try {
            boolean ignored_ = latch.await(value, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
