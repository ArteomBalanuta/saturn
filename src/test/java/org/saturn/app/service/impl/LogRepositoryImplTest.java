package org.saturn.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.model.MessageAuditEvent;

class LogRepositoryImplTest {
  @TempDir Path tempDir;

  @Test
  void persistsExplicitPublicAndWhisperVisibility() throws Exception {
    Path database = tempDir.resolve("audit.db");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE messages (
            id INTEGER PRIMARY KEY, trip TEXT, name TEXT NOT NULL, hash TEXT,
            message TEXT, created_on INTEGER NOT NULL, channel TEXT,
            visibility TEXT CHECK(visibility IN ('PUBLIC', 'WHISPER')))
          """);
      LogRepositoryImpl repository = new LogRepositoryImpl(connection);

      repository.logMessage(
          MessageAuditEvent.publicMessage(
              "trip-a", "alice", "hash-a", "hello room", "programming", 1));
      repository.logMessage(
          MessageAuditEvent.whisper("trip-a", "alice", "hash-a", "private text", "programming", 2));

      List<String> rows = new ArrayList<>();
      try (var resultSet =
          statement.executeQuery("SELECT message, visibility FROM messages ORDER BY id")) {
        while (resultSet.next()) {
          rows.add(resultSet.getString("message") + ":" + resultSet.getString("visibility"));
        }
      }
      assertEquals(List.of("hello room:PUBLIC", "private text:WHISPER"), rows);
    }
  }
}
