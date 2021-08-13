package org.saturn.app.service.impl;

import org.saturn.app.service.InternalService;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class InternalServiceImpl implements InternalService {
    private Connection connection;

    public InternalServiceImpl(Connection connection) {
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
