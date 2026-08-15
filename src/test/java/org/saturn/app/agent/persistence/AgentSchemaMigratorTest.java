package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.persistence.H2Database;

class AgentSchemaMigratorTest {
  @TempDir Path tempDir;

  @Test
  void createsAgentMemorySchemaIdempotently() throws Exception {
    Path database = tempDir.resolve("migration.db");

    AgentSchemaMigrator.migrate(database.toString());
    AgentSchemaMigrator.migrate(database.toString());

    try (var connection =
            java.sql.DriverManager.getConnection(H2Database.jdbcUrl(database.toString()));
        var resultSet =
            connection
                .getMetaData()
                .getTables(null, "public", "agent_memory", new String[] {"TABLE"})) {
      assertTrue(resultSet.next());
    }
  }
}
