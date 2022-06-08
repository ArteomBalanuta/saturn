package org.saturn.app.service.impl;

import org.saturn.app.service.LogService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class LogServiceImpl implements LogService {
    private Connection connection;

    public LogServiceImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void logEvent(String cmd, String status, long executedOn) {
        try {
            PreparedStatement logEvent = connection.prepareStatement("INSERT INTO internal_events ('cmd', 'status', 'executed_on') VALUES (?, ?, ?);");
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
