package org.saturn.app.service.impl;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.service.DataBaseService;

@Slf4j
public class DataBaseServiceImpl implements DataBaseService {
  private String databasePath;
  private Connection connection;

  public DataBaseServiceImpl(String path) {
    try {
      validateDbPath(path);
      this.databasePath = path;
      this.connection = setUpConnection();
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
    return DriverManager.getConnection(jdbcUrl);
  }

  @Override
  public Connection getConnection() {
    return this.connection;
  }
}
