package org.saturn.app.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.saturn.app.service.LogService;

public class LogServiceImpl implements LogService {
    private Connection connection;

    public LogServiceImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void log(String cmd, String status, long executedOn) {
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

}
