package org.saturn.app.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.model.MessageAuditEvent;
import org.saturn.app.service.LogRepository;
import org.saturn.app.util.SqlUtil;

@Slf4j
public class LogRepositoryImpl implements LogRepository {
  private final Connection connection;

  public LogRepositoryImpl(Connection connection) {
    this.connection = connection;
  }

  @Override
  public void logCommand(
      String trip, String cmd, String arguments, String status, String channel, long created_on) {
    try {
      PreparedStatement statement =
          connection.prepareStatement(
              SqlUtil
                  .INSERT_INTO_EXECUTED_COMMANDS_TRIP_COMMAND_NAME_ARGUMENTS_STATUS_CREATED_ON_VALUES);
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
  public void logMessage(MessageAuditEvent event) {
    try (PreparedStatement statement = connection.prepareStatement(SqlUtil.INSERT_INTO_MESSAGES)) {
      statement.setString(1, event.trip());
      statement.setString(2, event.nick());
      statement.setString(3, event.hash());
      statement.setString(4, event.message());
      statement.setLong(5, event.createdOn());
      statement.setString(6, event.channel());
      statement.setString(7, event.visibility().name());
      statement.executeUpdate();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Exception: ", e);
    }
  }
}
