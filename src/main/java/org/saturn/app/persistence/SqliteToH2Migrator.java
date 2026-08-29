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
    Path target = h2File(normalizedStem);
    if (!Files.isRegularFile(source)) {
      return false;
    }

    if (Files.exists(target)) {
      ExistingTargetState targetState = inspectExistingTarget(source, target);
      if (targetState == ExistingTargetState.COMPLETE) {
        archive(source);
        return true;
      }
      if (targetState == ExistingTargetState.INCOMPLETE) {
        try {
          resetH2Target(normalizedStem);
        } catch (IOException | SQLException exception) {
          throw new IllegalStateException(
              "Unable to remove incomplete H2 migration target " + target, exception);
        }
      } else {
        throw new IllegalStateException(
            "Legacy SQLite and H2 databases both contain data with different row counts. "
                + "Refusing to overwrite "
                + target);
      }
    }

    Map<String, Long> expectedRows = new LinkedHashMap<>();
    try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + source);
        Connection h2 =
            DriverManager.getConnection(H2Database.jdbcUrl(normalizedStem.toString()))) {
      h2.setAutoCommit(false);
      try {
        List<SqliteObject> tables = readObjects(sqlite, "table");
        createTables(h2, tables);
        setReferentialIntegrity(h2, false);
        try {
          for (SqliteObject table : tables) {
            long count = copyRows(sqlite, h2, table.name());
            expectedRows.put(table.name(), count);
            resetIdentity(h2, table.name(), sqliteColumns(sqlite, table.name()), count);
          }
        } finally {
          setReferentialIntegrity(h2, true);
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
      deleteFailedMigrationFiles(normalizedStem, exception);
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

  private static void setReferentialIntegrity(Connection h2, boolean enabled) throws SQLException {
    try (Statement statement = h2.createStatement()) {
      statement.execute("SET REFERENTIAL_INTEGRITY " + (enabled ? "TRUE" : "FALSE"));
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
        // SQLite INTEGER values are 64-bit and Saturn stores epoch milliseconds in these columns.
        .replaceAll("(?i)\\bINTEGER\\b", "BIGINT")
        .replaceAll("(?i)\\bTEXT\\b", "VARCHAR")
        .replaceAll("(?i)\\s+COLLATE\\s+NOCASE", "");
  }

  private static ExistingTargetState inspectExistingTarget(Path source, Path target) {
    try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + source);
        Connection h2 = DriverManager.getConnection(H2Database.jdbcUrl(target.toString()))) {
      List<SqliteObject> tables = readObjects(sqlite, "table");
      boolean sourceHasRows = false;
      boolean countsMatch = true;
      boolean targetIsEmpty = true;
      for (SqliteObject table : tables) {
        long sourceRows = rowCount(sqlite, table.name());
        long targetRows = rowCountIfPresent(h2, table.name());
        sourceHasRows |= sourceRows > 0;
        countsMatch &= targetRows == sourceRows;
        targetIsEmpty &= targetRows == 0;
      }
      if (countsMatch) {
        return ExistingTargetState.COMPLETE;
      }
      return sourceHasRows && targetIsEmpty
          ? ExistingTargetState.INCOMPLETE
          : ExistingTargetState.CONFLICT;
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Unable to inspect existing H2 migration target " + target, exception);
    }
  }

  private static long rowCount(Connection connection, String table) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + quote(table))) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }

  private static long rowCountIfPresent(Connection connection, String table) throws SQLException {
    try {
      return rowCount(connection, table);
    } catch (SQLException ignored) {
      return 0;
    }
  }

  private static void deleteFailedMigrationFiles(Path databaseStem, SQLException failure) {
    try {
      resetH2Target(databaseStem);
    } catch (IOException | SQLException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }

  /** Removes both H2's live in-memory catalog and its persisted files after a failed migration. */
  private static void resetH2Target(Path databaseStem) throws IOException, SQLException {
    try (Connection h2 = DriverManager.getConnection(H2Database.jdbcUrl(databaseStem.toString()));
        Statement statement = h2.createStatement()) {
      statement.execute("DROP ALL OBJECTS DELETE FILES");
    }
    deleteH2Files(databaseStem);
  }

  private static void deleteH2Files(Path databaseStem) throws IOException {
    Files.deleteIfExists(h2File(databaseStem));
    Files.deleteIfExists(Path.of(databaseStem + ".trace.db"));
    Files.deleteIfExists(Path.of(databaseStem + ".lock.db"));
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

  private enum ExistingTargetState {
    COMPLETE,
    INCOMPLETE,
    CONFLICT
  }
}
