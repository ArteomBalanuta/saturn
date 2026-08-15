package org.saturn.app.agent.persistence;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class AgentSchemaMigrator {
  private AgentSchemaMigrator() {}

  public static void migrate(String databasePath) {
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS agent_memory (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              identity_key TEXT NOT NULL,
              role TEXT NOT NULL CHECK(role IN ('user', 'assistant')),
              content TEXT NOT NULL,
              created_on INTEGER NOT NULL,
              expires_on INTEGER NOT NULL)
            """);
        statement.executeUpdate(
            """
            CREATE INDEX IF NOT EXISTS idx_agent_memory_identity_created
            ON agent_memory (identity_key, created_on DESC)
            """);
        statement.executeUpdate(
            """
            CREATE INDEX IF NOT EXISTS idx_agent_memory_expires
            ON agent_memory (expires_on)
            """);
        statement.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS agent_tool_memory (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              identity_key TEXT NOT NULL,
              tool_name TEXT NOT NULL,
              content TEXT NOT NULL,
              created_on INTEGER NOT NULL,
              expires_on INTEGER NOT NULL)
            """);
        statement.executeUpdate(
            """
            CREATE INDEX IF NOT EXISTS idx_agent_tool_memory_identity_created
            ON agent_tool_memory (identity_key, created_on DESC)
            """);
      }
      connection.commit();
    } catch (SQLException exception) {
      throw new AgentPersistenceException("Agent schema migration failed", exception);
    }
  }
}
