package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.persistence.H2Database;

class RepositoryAgentConversationContextProviderTest {
  @TempDir Path tempDir;

  @Test
  void rejectsMissingRepositoryAndNonPositiveMessageLimit() {
    assertThrows(
        NullPointerException.class, () -> new RepositoryAgentConversationContextProvider(null, 1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RepositoryAgentConversationContextProvider(
                (name, args, context) -> new JsonObject(), 0));
  }

  @Test
  void preservesResultsWithMissingOrMalformedRowsAndNullInboundValues() {
    AgentContext context =
        new AgentContext("lounge", "alice", "trip-a", "hash-a", false, List.of("alice"));
    JsonObject result = new JsonObject();
    JsonArray rows = new JsonArray();
    rows.add("not an object");
    JsonObject missingFields = new JsonObject();
    missingFields.add("name", com.google.gson.JsonNull.INSTANCE);
    missingFields.add("message", com.google.gson.JsonNull.INSTANCE);
    rows.add(missingFields);
    result.add("rows", rows);
    RepositoryAgentConversationContextProvider provider =
        new RepositoryAgentConversationContextProvider((name, args, supplied) -> result, 2);

    String loaded = provider.load(context, null, "message");
    assertEquals(result.toString(), loaded);
    assertEquals(2, result.getAsJsonArray("rows").size());

    assertEquals(result.toString(), provider.load(context, "nobody", "message"));

    assertEquals(result.toString(), provider.load(context, "alice", null));

    JsonObject withoutRows = new JsonObject();
    withoutRows.add("rows", new JsonObject());
    RepositoryAgentConversationContextProvider nonArrayProvider =
        new RepositoryAgentConversationContextProvider((name, args, supplied) -> withoutRows, 2);
    assertEquals(withoutRows.toString(), nonArrayProvider.load(context, "alice", "message"));
  }

  @Test
  void hydratesOnlyBoundedPublicMessagesFromTheCurrentRoom() throws Exception {
    Path database = tempDir.resolve("agent-context");
    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement()) {
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
            new H2AgentQueryRepository(database.toString()), 2);
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
    Path database = tempDir.resolve("agent-context-exclude");
    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO messages(trip,name,hash,message,created_on,channel,visibility) VALUES
          ('trip-a','alice','hash-a','earlier',1,'lounge','PUBLIC'),
          ('trip-a','alice','hash-a','current',2,'lounge','PUBLIC')
          """);
    }
    RepositoryAgentConversationContextProvider provider =
        new RepositoryAgentConversationContextProvider(
            new H2AgentQueryRepository(database.toString()), 10);
    AgentContext context =
        new AgentContext("lounge", "alice", "trip-a", "hash-a", false, List.of("alice"));

    String hydrated = provider.load(context, "alice", "current");

    assertTrue(hydrated.contains("earlier"));
    assertFalse(hydrated.contains("current"));
  }

  @Test
  void preservesAnOlderDuplicateWhenExcludingTheCurrentInboundMessage() throws Exception {
    Path database = tempDir.resolve("agent-context-duplicate");
    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement()) {
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
            new H2AgentQueryRepository(database.toString()), 10);
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
