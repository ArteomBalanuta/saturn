package org.saturn.app.service;

public interface LogRepository {
    void logCommand(String trip, String cmd,String arguments, String status, String channel, long created_on);
    void logMessage(String trip, String nick, String hash, String channel, String message, long timestamp);
}
