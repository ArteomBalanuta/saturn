package org.saturn.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class H2DatabaseTest {
  @TempDir Path tempDir;

  @Test
  void bootstrapsFreshH2DatabaseFromConfiguredStem() throws Exception {
    Path databaseStem = tempDir.resolve("saturn");

    H2Database.bootstrap(databaseStem.toString());

    assertTrue(Files.exists(tempDir.resolve("saturn.mv.db")));
    try (var connection = H2Database.open(databaseStem.toString());
        var statement = connection.prepareStatement("SELECT COUNT(*) FROM messages");
        var resultSet = statement.executeQuery()) {
      assertTrue(resultSet.next());
      assertEquals(0, resultSet.getLong(1));
    }
  }

  @Test
  void opensReadOnlyConnectionThroughSaturnDatabaseApi() throws Exception {
    Path databaseStem = tempDir.resolve("saturn-read-only");

    H2Database.bootstrap(databaseStem.toString());

    try (var connection = H2Database.openReadOnly(databaseStem.toString());
        var statement = connection.prepareStatement("SELECT COUNT(*) FROM messages");
        var resultSet = statement.executeQuery()) {
      assertTrue(resultSet.next());
      assertEquals(0, resultSet.getLong(1));
    }
  }
}
