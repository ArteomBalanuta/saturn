package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.persistence.H2Database;

class H2AgentSchemaRepositoryTest {
  @TempDir Path tempDir;
  private Path database;

  @BeforeEach
  void createDatabase() throws Exception {
    database = tempDir.resolve("schema");
    H2Database.bootstrap(database.toString());
  }

  @Test
  void describesUserTablesColumnsIndexesAndForeignKeys() {
    AgentDatabaseSchema schema =
        new H2AgentSchemaRepository(new H2ReadOnlyConnectionFactory(database.toString()))
            .describe();

    assertTrue(schema.tableNames().contains("messages"));
    AgentDatabaseSchema.Table messages = schema.findTable("MESSAGES").orElseThrow();
    assertTrue(messages.columns().stream().anyMatch(column -> column.name().equals("trip")));
    assertTrue(messages.columns().stream().anyMatch(column -> column.name().equals("hash")));
    assertTrue(
        messages.indexes().stream()
            .anyMatch(index -> index.columns().equals(java.util.List.of("trip", "created_on"))));
    assertEquals(2, schema.findTable("trip_names").orElseThrow().foreignKeys().size());
  }

  @Test
  void opensConnectionInReadOnlyMode() throws Exception {
    H2ReadOnlyConnectionFactory factory = new H2ReadOnlyConnectionFactory(database.toString());

    try (var connection = factory.open()) {
      assertTrue(connection.isValid(1));
    }
  }
}
