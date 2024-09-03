package org.saturn.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.service.LogRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Slf4j
public class LogRepositoryImpl implements LogRepository {
    private final Connection connection;

    public LogRepositoryImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void logCommand(String trip, String cmd,String arguments, String status, long created_on) {
            try {
                PreparedStatement statement = connection.prepareStatement("INSERT INTO executed_commands ('trip','command_name','arguments','status','created_on') VALUES (?, ?, ?);");
                statement.setString(1, trip);
                statement.setString(2, cmd);
                statement.setString(3, arguments);
                statement.setString(4, status);
                statement.setLong(5, created_on);

                statement.executeUpdate();

                statement.close();
            } catch (SQLException e) {
                log.info("Error: {}", e.getMessage());
                log.error("Exception: ", e);
            }
    }

    @Override
    public void logMessage(String trip, String name, String hash, String message, long timestamp) {
            try {
                PreparedStatement logEvent =
                        connection.prepareStatement("INSERT INTO messages ('trip', 'name', 'hash', 'message', 'created_on') VALUES (?, ?, ?, ?, ?);");
                logEvent.setString(1, trip);
                logEvent.setString(2, name);
                logEvent.setString(3, hash);
                logEvent.setString(4, message);
                logEvent.setLong(5, timestamp);

                logEvent.executeUpdate();

                logEvent.close();
            } catch (SQLException e) {
                log.info("Error: {}", e.getMessage());
                log.error("Exception: ", e);
            }
    }
}
