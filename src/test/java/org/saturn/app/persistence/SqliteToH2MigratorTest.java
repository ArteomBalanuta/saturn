package org.saturn.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteToH2MigratorTest {
  @TempDir Path tempDir;

  @Test
  void migratesLegacySqliteRowsAndArchivesTheSourceAfterVerification() throws Exception {
    Path databaseStem = tempDir.resolve("saturn");
    Path sqliteFile = tempDir.resolve("saturn.db");
    try (var sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
        var statement = sqlite.createStatement()) {
      statement.execute(
          "CREATE TABLE legacy_records (id INTEGER PRIMARY KEY AUTOINCREMENT, payload TEXT NOT NULL)");
      statement.execute(
          "CREATE UNIQUE INDEX idx_legacy_records_payload ON legacy_records(payload)");
      statement.execute("INSERT INTO legacy_records(payload) VALUES ('first'), ('second')");
    }

    H2Database.bootstrap(databaseStem.toString());

    try (var h2 = DriverManager.getConnection(H2Database.jdbcUrl(databaseStem.toString()));
        var statement = h2.prepareStatement("SELECT id, payload FROM legacy_records ORDER BY id");
        var resultSet = statement.executeQuery()) {
      assertTrue(resultSet.next());
      assertEquals(1, resultSet.getLong("id"));
      assertEquals("first", resultSet.getString("payload"));
      assertTrue(resultSet.next());
      assertEquals(2, resultSet.getLong("id"));
      assertEquals("second", resultSet.getString("payload"));
      assertFalse(resultSet.next());
    }
    assertTrue(Files.exists(tempDir.resolve("saturn.db.bak")));
    assertFalse(Files.exists(sqliteFile));
  }

  @Test
  void migratesLegacyIntegerEpochMillisecondsWithoutNarrowingTheValue() throws Exception {
    Path databaseStem = tempDir.resolve("epoch-milliseconds");
    Path sqliteFile = tempDir.resolve("epoch-milliseconds.db");
    long createdOn = 1_784_648_927_381L;
    try (var sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
        var statement = sqlite.createStatement()) {
      statement.execute(
          """
          CREATE TABLE banned_users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            trip TEXT, name TEXT, hash TEXT, reason TEXT,
            created_on INTEGER NOT NULL
          )
          """);
      statement.execute(
          "INSERT INTO banned_users(trip, name, hash, reason, created_on) VALUES "
              + "('trip', 'name', 'hash', 'reason', "
              + createdOn
              + ")");
    }
    // Simulate the empty file left behind when an earlier migration attempt rolled back.
    try (var ignored = DriverManager.getConnection(H2Database.jdbcUrl(databaseStem.toString()))) {
      // Opening the embedded database creates the incomplete target file.
    }

    H2Database.bootstrap(databaseStem.toString());

    try (var h2 = H2Database.open(databaseStem.toString());
        var statement = h2.prepareStatement("SELECT created_on FROM banned_users");
        var resultSet = statement.executeQuery()) {
      assertTrue(resultSet.next());
      assertEquals(createdOn, resultSet.getLong("created_on"));
    }
  }

  @Test
  void migratesForeignKeyRowsWhenLegacyTableOrderPlacesChildrenFirst() throws Exception {
    Path databaseStem = tempDir.resolve("foreign-keys");
    Path sqliteFile = tempDir.resolve("foreign-keys.db");
    try (var sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
        var statement = sqlite.createStatement()) {
      statement.execute(
          """
          CREATE TABLE child_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            parent_id INTEGER REFERENCES parent_records(id)
          )
          """);
      statement.execute("CREATE TABLE parent_records (id INTEGER PRIMARY KEY AUTOINCREMENT)");
      statement.execute("INSERT INTO parent_records(id) VALUES (1)");
      statement.execute("INSERT INTO child_records(id, parent_id) VALUES (1, 1)");
    }

    H2Database.bootstrap(databaseStem.toString());

    try (var h2 = H2Database.open(databaseStem.toString());
        var statement = h2.prepareStatement("SELECT parent_id FROM child_records");
        var resultSet = statement.executeQuery()) {
      assertTrue(resultSet.next());
      assertEquals(1L, resultSet.getLong("parent_id"));
    }
  }
}
