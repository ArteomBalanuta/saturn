package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.persistence.H2Database;

class H2AgentQueryRepositoryTest {
  @TempDir Path tempDir;
  private Path database;

  @BeforeEach
  void createDatabase() throws Exception {
    database = tempDir.resolve("agent");
    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM messages");
      statement.executeUpdate("DELETE FROM trips");
      statement.executeUpdate(
          """
          INSERT INTO messages(trip,name,message,created_on,channel,visibility) VALUES
          ('trip-a','alice','one',1,'programming','PUBLIC'),
          ('trip-b','bob','two',2,'programming','PUBLIC'),
          ('trip-a','alice','three',3,'programming','PUBLIC')
          """);
      statement.executeUpdate(
          "INSERT INTO trips(type,trip,created_on) VALUES ('REGULAR','trip-a',1),('USER','trip-b',2)");
    }
  }

  @Test
  void executesNamedQueriesAndScopesRecentMessagesToRequester() {
    H2AgentQueryRepository repository =
        new H2AgentQueryRepository(new H2ReadOnlyConnectionFactory(database.toString()));
    AgentContext alice =
        new AgentContext(
            "programming", "alice", "trip-a", "hash-a", false, List.of("alice", "bob"));

    JsonObject count = repository.execute("message_count", new JsonObject(), alice);
    JsonObject registeredUserCount =
        repository.execute("registered_user_count", new JsonObject(), alice);
    JsonObject recent =
        repository.execute("recent_messages_for_requester", new JsonObject(), alice);

    assertEquals(3, count.get("count").getAsInt());
    assertEquals(2, registeredUserCount.get("count").getAsInt());
    assertEquals(2, recent.getAsJsonArray("rows").size());
    assertEquals(
        "three",
        recent.getAsJsonArray("rows").get(0).getAsJsonObject().get("message").getAsString());
  }

  @Test
  void rejectsUnknownQueryInsteadOfExecutingSqlText() {
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
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
    try (var connection = H2Database.open(database.toString());
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO messages(trip,name,message,created_on,channel,visibility) "
                    + "VALUES (?,?,?,?,?,?)")) {
      for (int index = 0; index < 75; index++) {
        statement.setString(1, "trip-a");
        statement.setString(2, "alice");
        statement.setString(3, "message-" + index);
        statement.setLong(4, 10 + index);
        statement.setString(5, "programming");
        statement.setString(6, "PUBLIC");
        statement.addBatch();
      }
      statement.executeBatch();
    }
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    JsonObject arguments = new JsonObject();
    arguments.addProperty("limit", 1_000);
    AgentContext alice =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());
    AgentContext injection =
        new AgentContext("programming", "mallory", "trip-a' OR 1=1 --", "hash-m", false, List.of());

    JsonObject capped = repository.execute("recent_messages_for_requester", arguments, alice);
    JsonObject isolated =
        repository.execute("recent_messages_for_requester", new JsonObject(), injection);

    assertEquals(60, capped.getAsJsonArray("rows").size());
    assertEquals(0, isolated.getAsJsonArray("rows").size());
  }

  @Test
  void searchesNamedUserAcrossRoomsByDefaultAndSupportsAnExplicitRoom() throws Exception {
    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO messages(trip,name,message,created_on,channel,visibility) VALUES
          ('trip-j','Jetty','programming message',10,'programming','PUBLIC'),
          ('trip-j','Jetty','lounge message',11,'lounge','PUBLIC')
          """);
    }
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of("Jetty"));
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "jetty");
    arguments.addProperty("limit", 100);

    JsonObject allRooms = repository.execute("recent_messages_for_user", arguments, context);
    arguments.addProperty("room", "PROGRAMMING");
    JsonObject explicitRoom = repository.execute("recent_messages_for_user", arguments, context);

    assertEquals(2, allRooms.getAsJsonArray("rows").size());
    assertEquals(
        "lounge message",
        allRooms.getAsJsonArray("rows").get(0).getAsJsonObject().get("message").getAsString());
    JsonObject row = explicitRoom.getAsJsonArray("rows").get(0).getAsJsonObject();
    assertEquals("Jetty", row.get("name").getAsString());
    assertEquals("programming message", row.get("message").getAsString());
    assertEquals("programming", row.get("channel").getAsString());
    assertEquals(1, explicitRoom.getAsJsonArray("rows").size());
  }

  @Test
  void capsNamedUserHistoryAtFiveHundredWithoutIncreasingGeneralQueryLimit() throws Exception {
    try (var connection = H2Database.open(database.toString());
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO messages(trip,name,message,created_on,channel,visibility) "
                    + "VALUES (?,?,?,?,?,?)")) {
      for (int index = 0; index < 550; index++) {
        statement.setString(1, "trip-history");
        statement.setString(2, "sun");
        statement.setString(3, "message-" + index);
        statement.setLong(4, 10 + index);
        statement.setString(5, "programming");
        statement.setString(6, "PUBLIC");
        statement.addBatch();
      }
      statement.executeBatch();
    }

    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "sun");
    JsonObject history =
        repository.execute(
            "recent_messages_for_user",
            arguments,
            new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of()));

    assertEquals(500, history.getAsJsonArray("rows").size());
  }

  @Test
  void returnsRecentMessagesForAnExplicitRoomWithIdentityFields() throws Exception {
    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO messages(trip,name,hash,message,created_on,channel,visibility) VALUES
          ('trip-j','Jetty','hash-j','older lounge message',10,'lounge','PUBLIC'),
          ('trip-k','Korin','hash-k','newer lounge message',11,'lounge','PUBLIC')
          """);
    }
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());
    JsonObject arguments = new JsonObject();
    arguments.addProperty("room", "Lounge");
    arguments.addProperty("limit", 1);

    JsonObject result = repository.execute("recent_messages_for_room", arguments, context);
    arguments.addProperty("room", "lounge' OR 1=1 --");
    JsonObject injectionAttempt =
        repository.execute("recent_messages_for_room", arguments, context);

    assertEquals(1, result.getAsJsonArray("rows").size());
    JsonObject row = result.getAsJsonArray("rows").get(0).getAsJsonObject();
    assertEquals("Korin", row.get("name").getAsString());
    assertEquals("trip-k", row.get("trip").getAsString());
    assertEquals("hash-k", row.get("hash").getAsString());
    assertEquals("lounge", row.get("channel").getAsString());
    assertEquals(0, injectionAttempt.getAsJsonArray("rows").size());
  }

  @Test
  void excludesWhispersAndUnclassifiedLegacyRowsFromPublicHistory() throws Exception {
    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO messages(trip,name,hash,message,created_on,channel,visibility) VALUES
          ('trip-j','Jetty','hash-j','public text',20,'private-room','PUBLIC'),
          ('trip-j','Jetty','hash-j','whisper secret',21,'private-room','WHISPER'),
          ('trip-j','Jetty','hash-j','legacy unknown',22,'private-room',NULL)
          """);
    }
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("private-room", "alice", "trip-a", "hash-a", false, List.of());
    JsonObject roomArguments = new JsonObject();
    roomArguments.addProperty("room", "private-room");
    JsonObject userArguments = roomArguments.deepCopy();
    userArguments.addProperty("nick", "jetty");

    JsonObject room = repository.execute("recent_messages_for_room", roomArguments, context);
    JsonObject user = repository.execute("recent_messages_for_user", userArguments, context);

    assertEquals(1, room.getAsJsonArray("rows").size());
    assertEquals(1, user.getAsJsonArray("rows").size());
    assertEquals(
        "public text",
        room.getAsJsonArray("rows").get(0).getAsJsonObject().get("message").getAsString());
  }

  @Test
  void requiresNickForNamedUserMessageQuery() {
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());

    assertThrows(
        IllegalArgumentException.class,
        () -> repository.execute("recent_messages_for_user", new JsonObject(), context));
  }

  @Test
  void returnsKnownNicksForExplicitAndContextTrips() throws Exception {
    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO names(name,created_on) VALUES ('zeta',1),('alpha',2)");
      statement.executeUpdate(
          "INSERT INTO trip_names(trip_id,name_id) "
              + "SELECT t.id,n.id FROM trips t JOIN names n ON n.name IN ('zeta','alpha') "
              + "WHERE t.trip = 'trip-a'");
    }
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());

    JsonObject fromContext = repository.execute("known_nicks_for_trip", new JsonObject(), context);
    JsonObject explicit = new JsonObject();
    explicit.addProperty("trip", "trip-a");
    JsonObject fromExplicit = repository.execute("known_nicks_for_trip", explicit, context);

    assertEquals(2, fromContext.getAsJsonArray("rows").size());
    assertEquals(
        "alpha",
        fromContext.getAsJsonArray("rows").get(0).getAsJsonObject().get("name").getAsString());
    assertEquals(2, fromExplicit.getAsJsonArray("rows").size());
  }

  @Test
  void returnsEmptyRowsWhenRequesterTripIsMissing() {
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", " ", "hash-a", false, List.of());

    JsonObject result =
        repository.execute("recent_messages_for_requester", new JsonObject(), context);

    assertTrue(result.getAsJsonArray("rows").isEmpty());
  }

  @Test
  void returnsEmptyRowsWhenRequesterTripIsNull() {
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", null, "hash-a", false, List.of());

    JsonObject result =
        repository.execute("recent_messages_for_requester", new JsonObject(), context);

    assertTrue(result.getAsJsonArray("rows").isEmpty());
  }

  @Test
  void returnsEmptyRowsWhenKnownNicksTripIsBlank() {
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());
    JsonObject arguments = new JsonObject();
    arguments.addProperty("trip", " ");

    JsonObject result = repository.execute("known_nicks_for_trip", arguments, context);

    assertTrue(result.getAsJsonArray("rows").isEmpty());
  }

  @Test
  void rejectsInvalidRoomArgumentsBeforeOpeningTheDatabase() {
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());
    JsonObject arguments = new JsonObject();
    arguments.addProperty("room", " ");

    assertThrows(
        IllegalArgumentException.class,
        () -> repository.execute("recent_messages_for_room", arguments, context));
  }

  @Test
  void rejectsNonStringNickAndRoomArguments() {
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());
    JsonObject nickArguments = new JsonObject();
    nickArguments.addProperty("nick", 42);
    JsonObject roomArguments = new JsonObject();
    roomArguments.addProperty("room", 42);

    assertThrows(
        IllegalArgumentException.class,
        () -> repository.execute("recent_messages_for_user", nickArguments, context));
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.execute("recent_messages_for_room", roomArguments, context));
  }

  @Test
  void clampsNonPositiveLimitsToOneRow() {
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());
    JsonObject arguments = new JsonObject();
    arguments.addProperty("limit", 0);

    JsonObject result = repository.execute("recent_messages_for_room", arguments, context);

    assertEquals(1, result.getAsJsonArray("rows").size());
  }

  @Test
  void wrapsNamedQuerySqlFailuresWithoutDiscardingTheDatabaseCause() throws Exception {
    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("ALTER TABLE messages RENAME COLUMN visibility TO visibility_bad");
    }
    H2AgentQueryRepository repository = new H2AgentQueryRepository(database.toString());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());

    AgentPersistenceException exception =
        assertThrows(
            AgentPersistenceException.class,
            () -> repository.execute("message_count", new JsonObject(), context));

    assertEquals("Agent count query failed", exception.getMessage());
    assertTrue(exception.getCause() instanceof java.sql.SQLException);
  }
}
