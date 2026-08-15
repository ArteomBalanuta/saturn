package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.agent.AgentContext;

class SqliteAgentQueryRepositoryTest {
  @TempDir Path tempDir;
  private Path database;

  @BeforeEach
  void createDatabase() throws Exception {
    database = tempDir.resolve("agent.db");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE messages (
            id INTEGER PRIMARY KEY, trip TEXT, name TEXT NOT NULL, hash TEXT,
            message TEXT, created_on INTEGER NOT NULL, channel TEXT)
          """);
      statement.executeUpdate("CREATE TABLE trips (id INTEGER PRIMARY KEY, type TEXT, trip TEXT)");
      statement.executeUpdate(
          """
          INSERT INTO messages(trip,name,message,created_on,channel) VALUES
          ('trip-a','alice','one',1,'programming'),
          ('trip-b','bob','two',2,'programming'),
          ('trip-a','alice','three',3,'programming')
          """);
      statement.executeUpdate(
          "INSERT INTO trips(type,trip) VALUES ('REGULAR','trip-a'),('USER','trip-b')");
    }
  }

  @Test
  void executesNamedQueriesAndScopesRecentMessagesToRequester() {
    SqliteAgentQueryRepository repository = new SqliteAgentQueryRepository(database.toString());
    AgentContext alice =
        new AgentContext(
            "programming", "alice", "trip-a", "hash-a", false, List.of("alice", "bob"));

    JsonObject count = repository.execute("message_count", new JsonObject(), alice);
    JsonObject recent =
        repository.execute("recent_messages_for_requester", new JsonObject(), alice);

    assertEquals(3, count.get("count").getAsInt());
    assertEquals(2, recent.getAsJsonArray("rows").size());
    assertEquals(
        "three",
        recent.getAsJsonArray("rows").get(0).getAsJsonObject().get("message").getAsString());
  }

  @Test
  void rejectsUnknownQueryInsteadOfExecutingSqlText() {
    SqliteAgentQueryRepository repository = new SqliteAgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            repository.execute(
                "SELECT * FROM messages", JsonParser.parseString("{}").getAsJsonObject(), context));
  }

  @Test
  void capsRowsAndUsesRequesterTripAsPreparedParameter() throws Exception {
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO messages(trip,name,message,created_on,channel) VALUES (?,?,?,?,?)")) {
      for (int index = 0; index < 25; index++) {
        statement.setString(1, "trip-a");
        statement.setString(2, "alice");
        statement.setString(3, "message-" + index);
        statement.setLong(4, 10 + index);
        statement.setString(5, "programming");
        statement.addBatch();
      }
      statement.executeBatch();
    }
    SqliteAgentQueryRepository repository = new SqliteAgentQueryRepository(database.toString());
    JsonObject arguments = new JsonObject();
    arguments.addProperty("limit", 1_000);
    AgentContext alice =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());
    AgentContext injection =
        new AgentContext("programming", "mallory", "trip-a' OR 1=1 --", "hash-m", false, List.of());

    JsonObject capped = repository.execute("recent_messages_for_requester", arguments, alice);
    JsonObject isolated =
        repository.execute("recent_messages_for_requester", new JsonObject(), injection);

    assertEquals(20, capped.getAsJsonArray("rows").size());
    assertEquals(0, isolated.getAsJsonArray("rows").size());
  }

  @Test
  void returnsNamedUserMessagesOnlyFromCurrentRoom() throws Exception {
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO messages(trip,name,message,created_on,channel) VALUES
          ('trip-j','Jetty','programming message',10,'programming'),
          ('trip-j','Jetty','lounge message',11,'lounge')
          """);
    }
    SqliteAgentQueryRepository repository = new SqliteAgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of("Jetty"));
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "jetty");
    arguments.addProperty("limit", 100);

    JsonObject result = repository.execute("recent_messages_for_user", arguments, context);

    assertEquals(1, result.getAsJsonArray("rows").size());
    JsonObject row = result.getAsJsonArray("rows").get(0).getAsJsonObject();
    assertEquals("Jetty", row.get("name").getAsString());
    assertEquals("programming message", row.get("message").getAsString());
    assertEquals("programming", row.get("channel").getAsString());
  }

  @Test
  void requiresNickForNamedUserMessageQuery() {
    SqliteAgentQueryRepository repository = new SqliteAgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());

    assertThrows(
        IllegalArgumentException.class,
        () -> repository.execute("recent_messages_for_user", new JsonObject(), context));
  }
}
