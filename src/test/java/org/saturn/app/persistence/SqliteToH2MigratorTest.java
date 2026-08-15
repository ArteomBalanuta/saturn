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
}
