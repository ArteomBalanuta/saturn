package org.saturn.app.service.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class MessageSchemaMigrator {
  private static final String VISIBILITY_COLUMN =
      "ALTER TABLE messages ADD COLUMN visibility TEXT "
          + "CHECK(visibility IN ('PUBLIC', 'WHISPER'))";

  private MessageSchemaMigrator() {}

  static void migrate(Connection connection) throws SQLException {
    if (!tableExists(connection, "messages")) {
      return;
    }
    if (!columnExists(connection, "messages", "visibility")) {
      addVisibilityColumn(connection);
    }
    createIndexes(connection);
  }

  private static void addVisibilityColumn(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(VISIBILITY_COLUMN);
    } catch (SQLException exception) {
      // Another engine may have completed the same startup migration concurrently.
      if (!columnExists(connection, "messages", "visibility")) {
        throw exception;
      }
    }
  }

  private static void createIndexes(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_agent_messages_name_room_visibility_created
          ON messages (
            name COLLATE NOCASE,
            channel COLLATE NOCASE,
            visibility,
            created_on DESC,
            id DESC)
          """);
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_agent_messages_name_visibility_created
          ON messages (name COLLATE NOCASE, visibility, created_on DESC, id DESC)
          """);
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_agent_messages_room_visibility_created
          ON messages (channel COLLATE NOCASE, visibility, created_on DESC, id DESC)
          """);
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_agent_messages_trip_visibility_created
          ON messages (trip, visibility, created_on DESC, id DESC)
          """);
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_agent_messages_visibility
          ON messages (visibility)
          """);
    }
  }

  private static boolean tableExists(Connection connection, String table) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
      statement.setString(1, table);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  private static boolean columnExists(Connection connection, String table, String column)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
      while (resultSet.next()) {
        if (column.equalsIgnoreCase(resultSet.getString("name"))) {
          return true;
        }
      }
      return false;
    }
  }
}
