package org.saturn.app.agent.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

final class H2TransactionExecutor {
  private H2TransactionExecutor() {}

  static void execute(Connection connection, SqlWork work) throws SQLException {
    Objects.requireNonNull(connection, "connection");
    Objects.requireNonNull(work, "work");
    boolean originalAutoCommit = connection.getAutoCommit();
    try {
      connection.setAutoCommit(false);
      work.execute(connection);
      connection.commit();
    } catch (SQLException exception) {
      try {
        connection.rollback();
      } catch (SQLException rollbackFailure) {
        exception.addSuppressed(rollbackFailure);
      }
      throw exception;
    } finally {
      if (connection.getAutoCommit() != originalAutoCommit) {
        connection.setAutoCommit(originalAutoCommit);
      }
    }
  }

  @FunctionalInterface
  interface SqlWork {
    void execute(Connection connection) throws SQLException;
  }
}
