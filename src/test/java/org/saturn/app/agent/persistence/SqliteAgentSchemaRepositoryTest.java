package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteAgentSchemaRepositoryTest {
  @TempDir Path tempDir;
  private Path database;

  @BeforeEach
  void createDatabase() throws Exception {
    database = tempDir.resolve("schema.db");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE trips (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT NOT NULL,
            trip TEXT UNIQUE)
          """);
      statement.executeUpdate(
          """
          CREATE TABLE names (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE)
          """);
      statement.executeUpdate(
          """
          CREATE TABLE trip_names (
            trip_id INTEGER NOT NULL,
            name_id INTEGER NOT NULL,
            FOREIGN KEY (trip_id) REFERENCES trips(id),
            FOREIGN KEY (name_id) REFERENCES names(id))
          """);
      statement.executeUpdate(
          """
          CREATE TABLE messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            trip TEXT,
            name TEXT NOT NULL,
            hash TEXT,
            message TEXT,
            created_on INTEGER NOT NULL,
            channel TEXT)
          """);
      statement.executeUpdate(
          "CREATE INDEX idx_messages_trip_created ON messages(trip, created_on DESC)");
    }
  }

  @Test
  void describesUserTablesColumnsIndexesAndForeignKeys() {
    AgentDatabaseSchema schema =
        new SqliteAgentSchemaRepository(new SqliteReadOnlyConnectionFactory(database.toString()))
            .describe();

    assertTrue(schema.tableNames().contains("messages"));
    assertFalse(schema.tableNames().contains("sqlite_sequence"));
    AgentDatabaseSchema.Table messages = schema.findTable("MESSAGES").orElseThrow();
    assertTrue(messages.columns().stream().anyMatch(column -> column.name().equals("trip")));
    assertTrue(messages.columns().stream().anyMatch(column -> column.name().equals("hash")));
    assertTrue(
        messages.indexes().stream()
            .anyMatch(index -> index.columns().equals(java.util.List.of("trip", "created_on"))));
    assertEquals(2, schema.findTable("trip_names").orElseThrow().foreignKeys().size());
  }

  @Test
  void opensConnectionInReadOnlyAndQueryOnlyModes() throws Exception {
    SqliteReadOnlyConnectionFactory factory =
        new SqliteReadOnlyConnectionFactory(database.toString());

    try (var connection = factory.open();
        Statement statement = connection.createStatement();
        var result = statement.executeQuery("PRAGMA query_only")) {
      assertTrue(connection.isReadOnly());
      assertEquals(1, result.getInt(1));
      assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM messages"));
    }
  }
}
