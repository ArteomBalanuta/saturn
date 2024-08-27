package org.saturn.app.service.impl;

import org.saturn.app.service.LogService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import static org.saturn.app.util.DateUtil.formatRfc1123;

public class LogServiceImpl implements LogService {
    private final boolean isSql;

    private final Connection connection;

    public LogServiceImpl(Connection connection, boolean isSql) {
        this.connection = connection;
        this.isSql = isSql;
    }

    @Override
    public void logEvent(String cmd, String status, long executedOn) {
        if (!isSql) {
            return;
        }
        try {
            PreparedStatement logEvent = connection.prepareStatement("INSERT INTO internal_events ('cmd', 'status', 'created_on') VALUES (?, ?, ?);");
            logEvent.setString(1, cmd);
            logEvent.setString(2, status);
            logEvent.setLong(3, executedOn);
            
            logEvent.executeUpdate();

            logEvent.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void logMessage(String trip, String nick, String hash, String message, long timestamp) {
        if (!isSql) {
            System.out.println("[" + formatRfc1123(timestamp, TimeUnit.SECONDS, "UTC") + "] " + hash + " " + trip + " " + nick + ": " + message);
            return;
        }
        try {
            PreparedStatement logEvent = 
            connection.prepareStatement("INSERT INTO messages ('trip', 'nick', 'hash', 'message', 'created_on') VALUES (?, ?, ?, ?, ?);");
            logEvent.setString(1, trip);
            logEvent.setString(2, nick);
            logEvent.setString(3, hash);
            logEvent.setString(4, message);
            logEvent.setLong(5, timestamp);
            
            logEvent.executeUpdate();

            logEvent.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



}
