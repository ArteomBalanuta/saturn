package org.saturn.app.service.impl;

import java.sql.Connection;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.persistence.H2Database;
import org.saturn.app.service.DataBaseService;

@Slf4j
public class DataBaseServiceImpl implements DataBaseService {
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
    H2Database.absoluteStem(path);
  }

  private Connection setUpConnection() throws SQLException {
    log.debug("Opening H2 database stem: {}", databasePath);
    Connection connection = H2Database.open(databasePath);
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
    MessageSchemaMigrator.migrate(connection);
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
