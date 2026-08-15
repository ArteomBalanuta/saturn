package org.saturn.app.agent.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.saturn.app.persistence.H2Database;

public final class H2ReadOnlyConnectionFactory {
  private final String databasePath;

  public H2ReadOnlyConnectionFactory(String databasePath) {
    this.databasePath = Objects.requireNonNull(databasePath, "databasePath");
  }

  public Connection open() throws SQLException {
    return H2Database.openReadOnly(databasePath);
  }
}
