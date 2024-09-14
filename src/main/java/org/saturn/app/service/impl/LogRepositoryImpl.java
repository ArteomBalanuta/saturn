package org.saturn.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.service.LogRepository;
import org.saturn.app.util.SqlUtil;

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
    public void logCommand(String trip, String cmd,String arguments, String status, String channel, long created_on) {
            try {
                PreparedStatement statement = connection.prepareStatement(SqlUtil.INSERT_INTO_EXECUTED_COMMANDS_TRIP_COMMAND_NAME_ARGUMENTS_STATUS_CREATED_ON_VALUES);
                statement.setString(1, trip);
                statement.setString(2, cmd);
                statement.setString(3, arguments);
                statement.setString(4, status);
                statement.setLong(5, created_on);
                statement.setString(6, channel);

                statement.executeUpdate();

                statement.close();
            } catch (SQLException e) {
                log.info("Error: {}", e.getMessage());
                log.error("Exception: ", e);
            }
    }

    @Override
    public void logMessage(String trip, String name, String hash, String message, String channel, long timestamp) {
            try {
                PreparedStatement logEvent =
                        connection.prepareStatement(SqlUtil.INSERT_INTO_MESSAGES_TRIP_NAME_HASH_MESSAGE_CREATED_ON_VALUES);
                logEvent.setString(1, trip);
                logEvent.setString(2, name);
                logEvent.setString(3, hash);
                logEvent.setString(4, message);
                logEvent.setLong(5, timestamp);
                logEvent.setString(6, channel);

                logEvent.executeUpdate();

                logEvent.close();
            } catch (SQLException e) {
                log.info("Error: {}", e.getMessage());
                log.error("Exception: ", e);
            }
    }
}
