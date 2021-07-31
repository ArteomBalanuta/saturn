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
    public InternalServiceImpl() {
    }

    @Override
    public void log(String cmd, String status, long executedOn) {
        try {
            Connection connection = Connect.connect();
            PreparedStatement logEvent = connection.prepareStatement("INSERT INTO internal_events ('cmd', 'status', 'executed_on') VALUES (?, ?, ?);");
            logEvent.setString(1, cmd);
            logEvent.setString(2, status);
            logEvent.setLong(3, executedOn);
            
            logEvent.executeUpdate();

            logEvent.close();
            connection.close();
            
        } catch (URISyntaxException | SQLException e) {
            e.printStackTrace();
        }
    }
    
    private static class Connect {
        public static Connection connect() throws URISyntaxException {
            URL resource = Connect.class.getResource("/hackchat.db");
            String dbPath = Paths.get(resource.toURI()).toFile().getAbsolutePath();
            String url = "jdbc:sqlite:" + dbPath;
            try {
                return DriverManager.getConnection(url);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
