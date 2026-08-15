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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    AgentConfig config = config(1, Duration.ofHours(1));

    store.append(alice, "old question", "old answer", config);
    store.append(alice, "new question", "new answer", config);

    var messages = store.load(alice, config);
    assertEquals(
        List.of("new question", "new answer"),
        messages.stream().map(message -> message.content()).toList());
  }

  @Test
  void sharesPublicConversationAcrossUsersInTheSameRoom() {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);
    SqliteAgentMemoryStore store = new SqliteAgentMemoryStore(database.toString(), clock);
    AgentConfig config = config(2, Duration.ofHours(1));

    store.append(context("alice", "trip-a"), "alice question", "shared answer", config);

    assertEquals(
        List.of("alice question", "shared answer"),
        store.load(context("bob", "trip-b"), config).stream()
            .map(message -> message.content())
            .toList());
  }

  @Test
  void keepsWhispersOutOfPublicAndOtherUsersPrivateMemory() {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);
    SqliteAgentMemoryStore store = new SqliteAgentMemoryStore(database.toString(), clock);
    AgentConfig config = config(2, Duration.ofHours(1));
    AgentContext aliceWhisper = whisperContext("alice", "trip-a");

    store.append(aliceWhisper, "private question", "private answer", config);

    assertTrue(store.load(context("alice", "trip-a"), config).isEmpty());
    assertTrue(store.load(whisperContext("bob", "trip-b"), config).isEmpty());
    assertEquals(
        List.of("private question", "private answer"),
        store.load(aliceWhisper, config).stream().map(message -> message.content()).toList());
  }

  @Test
  void loadsHistoryWithoutCompetingForTheDatabaseWriteLock() throws Exception {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);
    SqliteAgentMemoryStore store = new SqliteAgentMemoryStore(database.toString(), clock);
    AgentConfig config = config(2, Duration.ofHours(1));
    AgentContext alice = context("alice", "trip-a");
    store.append(alice, "question", "answer", config);

    var blocker = DriverManager.getConnection("jdbc:sqlite:" + database);
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      blocker.setAutoCommit(false);
      try (Statement statement = blocker.createStatement()) {
        statement.executeUpdate(
            "UPDATE agent_memory SET content = 'uncommitted' WHERE identity_key = 'missing'");
        statement.executeUpdate(
            "INSERT INTO agent_memory(identity_key, role, content, created_on, expires_on) "
                + "VALUES ('blocker', 'user', 'uncommitted', 100, 200)");
      }

      var load = executor.submit(() -> store.load(alice, config));

      assertEquals(
          List.of("question", "answer"),
          load.get(500, TimeUnit.MILLISECONDS).stream().map(message -> message.content()).toList());
    } finally {
      blocker.rollback();
      blocker.close();
      executor.close();
    }
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

  private AgentContext whisperContext(String nick, String trip) {
    return new AgentContext("programming", nick, trip, "hash-" + nick, true, List.of());
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
