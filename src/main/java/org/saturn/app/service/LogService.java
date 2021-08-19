package org.saturn.app.service;

public interface LogService {
    void logEvent(String cmd, String status, long timestamp);
    void logMessage(String trip, String nick, String hash, String message, long timestamp);
}
