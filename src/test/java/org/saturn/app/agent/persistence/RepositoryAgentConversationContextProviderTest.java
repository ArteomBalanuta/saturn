package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.agent.AgentContext;

class RepositoryAgentConversationContextProviderTest {
  @TempDir Path tempDir;

  @Test
  void hydratesOnlyBoundedPublicMessagesFromTheCurrentRoom() throws Exception {
    Path database = tempDir.resolve("agent-context.db");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE messages (
            id INTEGER PRIMARY KEY, trip TEXT, name TEXT NOT NULL, hash TEXT,
            message TEXT, created_on INTEGER NOT NULL, channel TEXT, visibility TEXT)
          """);
      statement.executeUpdate(
          """
          INSERT INTO messages(trip,name,hash,message,created_on,channel,visibility) VALUES
          ('trip-a','alice','hash-a','older public',1,'lounge','PUBLIC'),
          ('trip-b','bob','hash-b','newer public',2,'lounge','PUBLIC'),
          ('trip-c','charlie','hash-c','newest public',3,'lounge','PUBLIC'),
          ('trip-a','alice','hash-a','whisper secret',4,'lounge','WHISPER'),
          ('trip-a','alice','hash-a','legacy unknown',5,'lounge',NULL),
          ('trip-a','alice','hash-a','other room',6,'programming','PUBLIC')
          """);
    }
    RepositoryAgentConversationContextProvider provider =
        new RepositoryAgentConversationContextProvider(
            new SqliteAgentQueryRepository(database.toString()), 2);
    AgentContext context =
        new AgentContext("lounge", "alice", "trip-a", "hash-a", false, List.of("alice"));

    String hydrated = provider.load(context);

    var rows = JsonParser.parseString(hydrated).getAsJsonObject().getAsJsonArray("rows");
    assertEquals(2, rows.size());
    assertEquals("newer public", rows.get(0).getAsJsonObject().get("message").getAsString());
    assertTrue(hydrated.contains("newer public"));
    assertFalse(hydrated.contains("older public"));
    assertFalse(hydrated.contains("whisper secret"));
    assertFalse(hydrated.contains("legacy unknown"));
    assertFalse(hydrated.contains("other room"));
  }

  @Test
  void excludesTheNewestMatchingInboundMessageFromRoomContext() throws Exception {
    Path database = tempDir.resolve("agent-context-exclude.db");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE messages (
            id INTEGER PRIMARY KEY, trip TEXT, name TEXT NOT NULL, hash TEXT,
            message TEXT, created_on INTEGER NOT NULL, channel TEXT, visibility TEXT)
          """);
      statement.executeUpdate(
          """
          INSERT INTO messages(trip,name,hash,message,created_on,channel,visibility) VALUES
          ('trip-a','alice','hash-a','earlier',1,'lounge','PUBLIC'),
          ('trip-a','alice','hash-a','current',2,'lounge','PUBLIC')
          """);
    }
    RepositoryAgentConversationContextProvider provider =
        new RepositoryAgentConversationContextProvider(
            new SqliteAgentQueryRepository(database.toString()), 10);
    AgentContext context =
        new AgentContext("lounge", "alice", "trip-a", "hash-a", false, List.of("alice"));

    String hydrated = provider.load(context, "alice", "current");

    assertTrue(hydrated.contains("earlier"));
    assertFalse(hydrated.contains("current"));
  }

  @Test
  void preservesAnOlderDuplicateWhenExcludingTheCurrentInboundMessage() throws Exception {
    Path database = tempDir.resolve("agent-context-duplicate.db");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE messages (
            id INTEGER PRIMARY KEY, trip TEXT, name TEXT NOT NULL, hash TEXT,
            message TEXT, created_on INTEGER NOT NULL, channel TEXT, visibility TEXT)
          """);
      statement.executeUpdate(
          """
          INSERT INTO messages(trip,name,hash,message,created_on,channel,visibility) VALUES
          ('trip-a','alice','hash-a','repeat',1,'lounge','PUBLIC'),
          ('trip-b','bob','hash-b','between',2,'lounge','PUBLIC'),
          ('trip-a','alice','hash-a','repeat',3,'lounge','PUBLIC')
          """);
    }
    RepositoryAgentConversationContextProvider provider =
        new RepositoryAgentConversationContextProvider(
            new SqliteAgentQueryRepository(database.toString()), 10);
    AgentContext context =
        new AgentContext("lounge", "alice", "trip-a", "hash-a", false, List.of("alice"));

    var rows =
        JsonParser.parseString(provider.load(context, "alice", "repeat"))
            .getAsJsonObject()
            .getAsJsonArray("rows");

    assertEquals(2, rows.size());
    assertEquals("repeat", rows.get(0).getAsJsonObject().get("message").getAsString());
    assertEquals("between", rows.get(1).getAsJsonObject().get("message").getAsString());
  }
}
