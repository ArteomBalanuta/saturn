package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.agent.AgentConfig;
import org.saturn.app.agent.AgentContext;

class SqliteAgentMemoryStoreTest {
  @TempDir Path tempDir;
  private Path database;

  @BeforeEach
  void createDatabase() throws Exception {
    database = tempDir.resolve("memory.db");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE agent_memory (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            identity_key TEXT NOT NULL,
            role TEXT NOT NULL CHECK(role IN ('user','assistant')),
            content TEXT NOT NULL,
            created_on INTEGER NOT NULL,
            expires_on INTEGER NOT NULL)
          """);
    }
  }

  @Test
  void loadsOnlyLatestConfiguredTurnsForStableIdentity() {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);
    SqliteAgentMemoryStore store = new SqliteAgentMemoryStore(database.toString(), clock);
    AgentContext alice = context("alice", "trip-a");
    AgentContext bob = context("bob", "trip-b");
    AgentConfig config = config(1, Duration.ofHours(1));

    store.append(alice, "old question", "old answer", config);
    store.append(alice, "new question", "new answer", config);
    store.append(bob, "bob question", "bob answer", config);

    var messages = store.load(alice, config);
    assertEquals(
        List.of("new question", "new answer"),
        messages.stream().map(message -> message.content()).toList());
  }

  @Test
  void excludesExpiredMemory() {
    AgentConfig config = config(2, Duration.ofHours(1));
    AgentContext alice = context("alice", "trip-a");
    new SqliteAgentMemoryStore(
            database.toString(), Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC))
        .append(alice, "question", "answer", config);

    var expired =
        new SqliteAgentMemoryStore(
                database.toString(), Clock.fixed(Instant.ofEpochSecond(3_701), ZoneOffset.UTC))
            .load(alice, config);

    assertTrue(expired.isEmpty());
  }

  @Test
  void isolatesMemoryForSameIdentityAcrossRooms() {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);
    SqliteAgentMemoryStore store = new SqliteAgentMemoryStore(database.toString(), clock);
    AgentConfig config = config(2, Duration.ofHours(1));
    AgentContext programming = context("alice", "trip-a");
    AgentContext privateRoom =
        new AgentContext("private-room", "alice", "trip-a", "hash-alice", false, List.of());

    store.append(privateRoom, "private question", "private answer", config);

    assertTrue(store.load(programming, config).isEmpty());
    assertEquals(
        List.of("private question", "private answer"),
        store.load(privateRoom, config).stream().map(message -> message.content()).toList());
  }

  private AgentContext context(String nick, String trip) {
    return new AgentContext("programming", nick, trip, "hash-" + nick, false, List.of());
  }

  private AgentConfig config(int turns, Duration ttl) {
    return new AgentConfig(
        true,
        URI.create("http://localhost"),
        Optional.empty(),
        "",
        Duration.ofSeconds(1),
        1,
        4,
        2,
        2,
        100,
        100,
        turns,
        ttl,
        0,
        Duration.ZERO);
  }
}
