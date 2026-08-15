package org.saturn.app.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Performs Saturn's one-time transactional migration from a legacy SQLite file to H2. */
public final class SqliteToH2Migrator {
  private SqliteToH2Migrator() {}

  /**
   * Migrates {@code <databaseStem>.db} into H2 when it exists and no H2 database has been created.
   * The source is renamed to {@code .db.bak} only after each copied table has the same row count.
   *
   * @return {@code true} when a legacy database was migrated
   */
  public static boolean migrateIfNeeded(Path databaseStem) {
    Path normalizedStem = H2Database.absoluteStem(databaseStem.toString());
    Path source = Path.of(normalizedStem + ".db");
    if (!Files.isRegularFile(source) || Files.exists(h2File(normalizedStem))) {
      return false;
    }

    Map<String, Long> expectedRows = new LinkedHashMap<>();
    try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + source);
        Connection h2 =
            DriverManager.getConnection(H2Database.jdbcUrl(normalizedStem.toString()))) {
      h2.setAutoCommit(false);
      try {
        List<SqliteObject> tables = readObjects(sqlite, "table");
        createTables(h2, tables);
        for (SqliteObject table : tables) {
          long count = copyRows(sqlite, h2, table.name());
          expectedRows.put(table.name(), count);
          resetIdentity(h2, table.name(), sqliteColumns(sqlite, table.name()), count);
        }
        for (SqliteObject index : readObjects(sqlite, "index")) {
          executeTranslated(h2, index.sql());
        }
        verifyRowCounts(h2, expectedRows);
        h2.commit();
      } catch (Exception exception) {
        rollback(h2, exception);
        throw exception;
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("SQLite to H2 migration failed for " + source, exception);
    }

    archive(source);
    return true;
  }

  private static List<SqliteObject> readObjects(Connection sqlite, String type)
      throws SQLException {
    String sql =
        "SELECT name, sql FROM sqlite_master WHERE type = ? AND name NOT LIKE 'sqlite_%' "
            + "AND sql IS NOT NULL ORDER BY name";
    List<SqliteObject> objects = new ArrayList<>();
    try (PreparedStatement statement = sqlite.prepareStatement(sql)) {
      statement.setString(1, type);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          objects.add(new SqliteObject(resultSet.getString("name"), resultSet.getString("sql")));
        }
      }
    }
    return objects;
  }

  private static List<String> sqliteColumns(Connection sqlite, String table) throws SQLException {
    List<String> columns = new ArrayList<>();
    try (Statement statement = sqlite.createStatement();
        ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + quote(table) + ")")) {
      while (resultSet.next()) {
        columns.add(resultSet.getString("name"));
      }
    }
    return columns;
  }

  private static void createTables(Connection h2, List<SqliteObject> tables) throws SQLException {
    List<SqliteObject> pending = new ArrayList<>(tables);
    SQLException lastFailure = null;
    while (!pending.isEmpty()) {
      boolean progressed = false;
      for (var iterator = pending.iterator(); iterator.hasNext(); ) {
        SqliteObject table = iterator.next();
        try {
          executeTranslated(h2, table.sql());
          iterator.remove();
          progressed = true;
        } catch (SQLException exception) {
          lastFailure = exception;
        }
      }
      if (!progressed) {
        throw lastFailure == null
            ? new SQLException("Unable to create migrated tables")
            : lastFailure;
      }
    }
  }

  private static long copyRows(Connection sqlite, Connection h2, String table) throws SQLException {
    List<String> columns = sqliteColumns(sqlite, table);
    if (columns.isEmpty()) {
      return 0;
    }
    String quotedColumns =
        columns.stream()
            .map(SqliteToH2Migrator::quote)
            .reduce((a, b) -> a + ", " + b)
            .orElseThrow();
    String placeholders =
        columns.stream().map(ignored -> "?").reduce((a, b) -> a + ", " + b).orElseThrow();
    String select = "SELECT " + quotedColumns + " FROM " + quote(table);
    String insert =
        "INSERT INTO " + quote(table) + " (" + quotedColumns + ") VALUES (" + placeholders + ")";
    long copied = 0;
    try (Statement sourceStatement = sqlite.createStatement();
        ResultSet rows = sourceStatement.executeQuery(select);
        PreparedStatement destination = h2.prepareStatement(insert)) {
      while (rows.next()) {
        for (int column = 1; column <= columns.size(); column++) {
          destination.setObject(column, rows.getObject(column));
        }
        destination.addBatch();
        copied++;
        if (copied % 500 == 0) {
          destination.executeBatch();
        }
      }
      destination.executeBatch();
    }
    return copied;
  }

  private static void resetIdentity(Connection h2, String table, List<String> columns, long copied)
      throws SQLException {
    if (copied == 0 || columns.stream().noneMatch(column -> "id".equalsIgnoreCase(column))) {
      return;
    }
    long next;
    try (Statement statement = h2.createStatement();
        ResultSet resultSet =
            statement.executeQuery("SELECT COALESCE(MAX(\"id\"), 0) + 1 FROM " + quote(table))) {
      resultSet.next();
      next = resultSet.getLong(1);
    }
    try (Statement statement = h2.createStatement()) {
      statement.execute(
          "ALTER TABLE " + quote(table) + " ALTER COLUMN \"id\" RESTART WITH " + next);
    }
  }

  private static void verifyRowCounts(Connection h2, Map<String, Long> expectedRows)
      throws SQLException {
    for (Map.Entry<String, Long> entry : expectedRows.entrySet()) {
      try (Statement statement = h2.createStatement();
          ResultSet resultSet =
              statement.executeQuery("SELECT COUNT(*) FROM " + quote(entry.getKey()))) {
        resultSet.next();
        long actual = resultSet.getLong(1);
        if (actual != entry.getValue()) {
          throw new SQLException(
              "Row-count verification failed for "
                  + entry.getKey()
                  + ": expected="
                  + entry.getValue()
                  + ", actual="
                  + actual);
        }
      }
    }
  }

  private static void executeTranslated(Connection h2, String sqliteSql) throws SQLException {
    try (Statement statement = h2.createStatement()) {
      statement.execute(translate(sqliteSql));
    }
  }

  static String translate(String sqliteSql) {
    Objects.requireNonNull(sqliteSql, "sqliteSql");
    return sqliteSql
        .replaceAll(
            "(?i)\\bINTEGER\\s+PRIMARY\\s+KEY\\s+AUTOINCREMENT\\b",
            "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY")
        .replaceAll("(?i)\\bAUTOINCREMENT\\b", "")
        .replaceAll("(?i)\\bTEXT\\b", "VARCHAR")
        .replaceAll("(?i)\\s+COLLATE\\s+NOCASE", "");
  }

  private static void rollback(Connection connection, Exception failure) throws SQLException {
    try {
      connection.rollback();
    } catch (SQLException rollbackFailure) {
      failure.addSuppressed(rollbackFailure);
    }
  }

  private static void archive(Path source) {
    try {
      Files.move(source, Path.of(source + ".bak"), StandardCopyOption.ATOMIC_MOVE);
      archiveIfPresent(Path.of(source + "-wal"));
      archiveIfPresent(Path.of(source + "-shm"));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "H2 data was copied but legacy SQLite source could not be archived: " + source,
          exception);
    }
  }

  private static void archiveIfPresent(Path source) throws IOException {
    if (Files.exists(source)) {
      Files.move(source, Path.of(source + ".bak"), StandardCopyOption.ATOMIC_MOVE);
    }
  }

  private static Path h2File(Path stem) {
    return Path.of(stem + ".mv.db");
  }

  private static String quote(String identifier) {
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }

  private record SqliteObject(String name, String sql) {}
}
