package org.saturn.app.agent.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.saturn.app.persistence.H2Database;

/** Creates read-only H2 connections for agent database access. */
public final class H2ReadOnlyConnectionFactory {
  private final String databasePath;

  /**
   * Implements the {@code H2ReadOnlyConnectionFactory} operation for this agent component.
   *
   * @param databasePath input argument used by this operation
   */
  public H2ReadOnlyConnectionFactory(String databasePath) {
    this.databasePath = Objects.requireNonNull(databasePath, "databasePath");
  }

  /**
   * Implements the {@code open} operation for this agent component.
   *
   * @return the operation result
   */
  public Connection open() throws SQLException {
    return H2Database.openReadOnly(databasePath);
  }
}
