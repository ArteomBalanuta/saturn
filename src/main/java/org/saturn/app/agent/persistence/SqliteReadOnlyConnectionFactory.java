package org.saturn.app.agent.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import org.sqlite.SQLiteConfig;

public final class SqliteReadOnlyConnectionFactory {
  private final String databasePath;

  public SqliteReadOnlyConnectionFactory(String databasePath) {
    this.databasePath = Objects.requireNonNull(databasePath, "databasePath");
  }

  public Connection open() throws SQLException {
    SQLiteConfig config = new SQLiteConfig();
    config.setReadOnly(true);
    config.enableLoadExtension(false);
    config.setBusyTimeout(1_000);
    Connection connection =
        DriverManager.getConnection("jdbc:sqlite:" + databasePath, config.toProperties());
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA query_only = ON");
    } catch (SQLException exception) {
      connection.close();
      throw exception;
    }
    return connection;
  }
}
