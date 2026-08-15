package org.saturn.app.service.impl;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.service.DataBaseService;

@Slf4j
public class DataBaseServiceImpl implements DataBaseService {
  private static final String ENABLE_FOREIGN_KEYS = "PRAGMA foreign_keys = ON";
  private static final String ENABLE_WAL = "PRAGMA journal_mode = WAL";
  private static final String SET_BUSY_TIMEOUT = "PRAGMA busy_timeout = 5000";
  private final String databasePath;

  public DataBaseServiceImpl(String path) {
    this.databasePath = path;
    try {
      validateDbPath(path);
    } catch (Exception e) {
      log.error("Error: {}", e.getMessage());
      log.error("Stack trace:", e);
      System.exit(1);
    }
  }

  protected void validateDbPath(String path) {
    File file = new File(path);
    if (!file.exists()) {
      log.error("Can't find database file, path: {}", path);
      throw new RuntimeException("No database file present, path: " + path);
    }
  }

  private Connection setUpConnection() throws SQLException {
    String jdbcUrl = "jdbc:sqlite:" + databasePath;
    log.debug("Using JDBC connection string: {}", jdbcUrl);
    Connection connection = DriverManager.getConnection(jdbcUrl);
    try {
      configureConnection(connection);
      return connection;
    } catch (SQLException exception) {
      try {
        connection.close();
      } catch (SQLException closeException) {
        exception.addSuppressed(closeException);
      }
      throw exception;
    }
  }

  private void configureConnection(Connection connection) throws SQLException {
    executePragma(connection, ENABLE_FOREIGN_KEYS);
    executePragma(connection, ENABLE_WAL);
    executePragma(connection, SET_BUSY_TIMEOUT);
    MessageSchemaMigrator.migrate(connection);
  }

  private void executePragma(Connection connection, String pragma) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(pragma)) {
      statement.execute();
    }
  }

  @Override
  public Connection getConnection() {
    try {
      return setUpConnection();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to open database connection", e);
    }
  }
}
