package org.saturn.app.agent.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Executes agent persistence work within H2 transactions. */
final class H2TransactionExecutor {
  /** Implements the {@code H2TransactionExecutor} operation for this agent component. */
  private H2TransactionExecutor() {}

  /**
   * Implements the {@code execute} operation for this agent component.
   *
   * @param connection input argument used by this operation
   * @param work input argument used by this operation
   */
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
  /** Defines the operation used to sql work. */
  /** Defines the operation used to sql work. */
  /**
   * Performs one unit of SQL work on the supplied transaction connection.
   *
   * @param connection transaction connection
   * @throws SQLException when the SQL operation fails
   */
  interface SqlWork {
    void execute(Connection connection) throws SQLException;
  }
}
