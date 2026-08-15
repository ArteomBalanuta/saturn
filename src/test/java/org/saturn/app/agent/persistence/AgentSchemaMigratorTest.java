package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentSchemaMigratorTest {
  @TempDir Path tempDir;

  @Test
  void createsAgentMemorySchemaIdempotently() throws Exception {
    Path database = tempDir.resolve("migration.db");

    AgentSchemaMigrator.migrate(database.toString());
    AgentSchemaMigrator.migrate(database.toString());

    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        var statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name ="
                    + " 'agent_memory'");
        var resultSet = statement.executeQuery()) {
      assertEquals(1, resultSet.getInt(1));
    }
  }
}
