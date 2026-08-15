package org.saturn.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataBaseServiceImplTest {
  @TempDir Path tempDir;

  @Test
  void migratesLegacyMessagesAsPublic() throws Exception {
    Path database = tempDir.resolve("legacy.db");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE messages (
            id INTEGER PRIMARY KEY, trip TEXT, name TEXT NOT NULL, hash TEXT,
            message TEXT, created_on INTEGER NOT NULL, channel TEXT)
          """);
      statement.executeUpdate(
          """
          INSERT INTO messages(trip,name,message,created_on,channel)
          VALUES ('trip-a','alice','legacy text',1,'programming')
          """);
    }

    try (var connection = new DataBaseServiceImpl(database.toString()).getConnection();
        Statement statement = connection.createStatement()) {
      Set<String> columns = new HashSet<>();
      try (var resultSet = statement.executeQuery("PRAGMA table_info(messages)")) {
        while (resultSet.next()) {
          columns.add(resultSet.getString("name"));
        }
      }
      assertTrue(columns.contains("visibility"));

      try (var resultSet = statement.executeQuery("SELECT visibility FROM messages WHERE id = 1")) {
        assertTrue(resultSet.next());
        assertEquals("PUBLIC", resultSet.getString("visibility"));
      }

      Set<String> indexes = new HashSet<>();
      try (var resultSet =
          statement.executeQuery(
              "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'messages'")) {
        while (resultSet.next()) {
          indexes.add(resultSet.getString("name"));
        }
      }
      assertTrue(indexes.contains("idx_agent_messages_name_room_visibility_created"));
      assertTrue(indexes.contains("idx_agent_messages_name_visibility_created"));
      assertTrue(indexes.contains("idx_agent_messages_room_visibility_created"));
      assertTrue(indexes.contains("idx_agent_messages_trip_visibility_created"));
    }
  }

  @Test
  void preservesWhispersWhenBackfillingLegacyVisibility() throws Exception {
    Path database = tempDir.resolve("visibility.db");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE messages (
            id INTEGER PRIMARY KEY, trip TEXT, name TEXT NOT NULL, hash TEXT,
            message TEXT, created_on INTEGER NOT NULL, channel TEXT,
            visibility TEXT CHECK(visibility IN ('PUBLIC', 'WHISPER')))
          """);
      statement.executeUpdate(
          """
          INSERT INTO messages(id, name, message, created_on, channel, visibility)
          VALUES
            (1, 'alice', 'legacy text', 1, 'programming', NULL),
            (2, 'alice', 'public text', 2, 'programming', 'PUBLIC'),
            (3, 'alice', 'private text', 3, 'programming', 'WHISPER')
          """);
    }

    try (var connection = new DataBaseServiceImpl(database.toString()).getConnection();
        Statement statement = connection.createStatement();
        var resultSet =
            statement.executeQuery(
                "SELECT id, visibility FROM messages ORDER BY id")) {
      assertTrue(resultSet.next());
      assertEquals("PUBLIC", resultSet.getString("visibility"));
      assertTrue(resultSet.next());
      assertEquals("PUBLIC", resultSet.getString("visibility"));
      assertTrue(resultSet.next());
      assertEquals("WHISPER", resultSet.getString("visibility"));
      assertFalse(resultSet.next());
    }
  }

  @Test
  void publicHistoryIndexesMatchCaseInsensitivePredicatesAndOrdering() throws Exception {
    Path database = tempDir.resolve("plans.db");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE messages (
            id INTEGER PRIMARY KEY, trip TEXT, name TEXT NOT NULL, hash TEXT,
            message TEXT, created_on INTEGER NOT NULL, channel TEXT)
          """);
    }

    try (var connection = new DataBaseServiceImpl(database.toString()).getConnection();
        var statement =
            connection.prepareStatement(
                """
                EXPLAIN QUERY PLAN
                SELECT name, trip, hash, message, created_on, channel
                FROM messages
                WHERE name = ? COLLATE NOCASE
                  AND channel = ? COLLATE NOCASE
                  AND visibility = 'PUBLIC'
                ORDER BY created_on DESC, id DESC
                LIMIT ?
                """)) {
      statement.setString(1, "alice");
      statement.setString(2, "programming");
      statement.setInt(3, 20);
      try (var resultSet = statement.executeQuery()) {
        assertTrue(resultSet.next());
        String plan = resultSet.getString("detail");
        assertTrue(plan.contains("idx_agent_messages_name_room_visibility_created"), plan);
        assertFalse(plan.contains("USE TEMP B-TREE"), plan);
      }

      try (var allRooms =
          connection.prepareStatement(
              """
              EXPLAIN QUERY PLAN
              SELECT name, trip, hash, message, created_on, channel
              FROM messages
              WHERE name = ? COLLATE NOCASE AND visibility = 'PUBLIC'
              ORDER BY created_on DESC, id DESC
              LIMIT ?
              """)) {
        allRooms.setString(1, "alice");
        allRooms.setInt(2, 20);
        try (var resultSet = allRooms.executeQuery()) {
          assertTrue(resultSet.next());
          String plan = resultSet.getString("detail");
          assertTrue(plan.contains("idx_agent_messages_name_visibility_created"), plan);
          assertFalse(plan.contains("USE TEMP B-TREE"), plan);
        }
      }
    }
  }
}
