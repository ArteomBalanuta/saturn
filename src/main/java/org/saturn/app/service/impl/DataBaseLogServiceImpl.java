package org.saturn.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.service.LogService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import static org.saturn.app.util.DateUtil.formatRfc1123;

@Slf4j
public class DataBaseLogServiceImpl implements LogService {
    private final boolean isSql;

    private final Connection connection;

    public DataBaseLogServiceImpl(Connection connection, boolean isSql) {
        this.connection = connection;
        this.isSql = isSql;
    }

    /* TODO: refactor the schema, make sure it logs executed CMDs. */
    @Override
    public void logEvent(String cmd, String status, long executedOn) {
        if (isSql) {
            try {
                PreparedStatement logEvent = connection.prepareStatement("INSERT INTO internal_events ('cmd', 'status', 'created_on') VALUES (?, ?, ?);");
                logEvent.setString(1, cmd);
                logEvent.setString(2, status);
                logEvent.setLong(3, executedOn);

                logEvent.executeUpdate();

                logEvent.close();
            } catch (SQLException e) {
                log.warn("Error: {}", e.getMessage());
                log.debug("Exception: ", e);
            }
        }
    }

    @Override
    public void logMessage(String trip, String nick, String hash, String message, long timestamp) {
        if (isSql) {
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
                log.warn("Error: {}", e.getMessage());
                log.debug("Exception: ", e);
            }
        }
    }
}
