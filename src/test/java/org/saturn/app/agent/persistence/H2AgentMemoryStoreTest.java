package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.persistence.H2Database;

class H2AgentMemoryStoreTest {
  @TempDir Path tempDir;
  private Path database;

  @BeforeEach
  void createDatabase() throws Exception {
    database = tempDir.resolve("memory");
    H2Database.bootstrap(database.toString());
  }

  @Test
  void loadsOnlyLatestConfiguredTurnsForStableIdentity() {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);
    H2AgentMemoryStore store = new H2AgentMemoryStore(database.toString(), clock);
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
    H2AgentMemoryStore store = new H2AgentMemoryStore(database.toString(), clock);
    AgentConfig config = config(2, Duration.ofHours(1));

    store.append(context("alice", "trip-a"), "alice question", "shared answer", config);

    assertEquals(
        List.of("alice question", "shared answer"),
        store.load(context("bob", "trip-b"), config).stream()
            .map(message -> message.content())
            .toList());
  }

  @Test
  void retainsToolEvidenceForTheNextTurn() {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);
    H2AgentMemoryStore store = new H2AgentMemoryStore(database.toString(), clock);
    AgentContext alice = context("alice", "trip-a");
    AgentConfig config = config(2, Duration.ofHours(1));

    store.append(alice, "who is jill", "Jill is active.", config);
    store.appendToolEvidence(alice, "user_message_history", "{\"nick\":\"jill\"}", config);

    var retainedEvidence =
        store.load(alice, config).stream()
            .filter(message -> message.content().contains("user_message_history"))
            .findFirst()
            .orElseThrow();

    assertEquals("system", retainedEvidence.role());
  }

  @Test
  void expiresToolEvidenceWithoutRetainingItInConversationMemory() {
    AgentContext alice = context("alice", "trip-a");
    AgentConfig config = config(2, Duration.ofHours(1));
    new H2AgentMemoryStore(
            database.toString(), Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC))
        .appendToolEvidence(alice, "room_users", "{\"count\":2}", config);

    var messages =
        new H2AgentMemoryStore(
                database.toString(), Clock.fixed(Instant.ofEpochSecond(3_701), ZoneOffset.UTC))
            .load(alice, config);

    assertTrue(messages.isEmpty());
  }

  @Test
  void keepsWhispersOutOfPublicAndOtherUsersPrivateMemory() {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);
    H2AgentMemoryStore store = new H2AgentMemoryStore(database.toString(), clock);
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
  void loadsPersistedHistory() {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);
    H2AgentMemoryStore store = new H2AgentMemoryStore(database.toString(), clock);
    AgentConfig config = config(2, Duration.ofHours(1));
    AgentContext alice = context("alice", "trip-a");
    store.append(alice, "question", "answer", config);

    assertEquals(
        List.of("question", "answer"),
        store.load(alice, config).stream().map(message -> message.content()).toList());
  }

  @Test
  void excludesExpiredMemory() {
    AgentConfig config = config(2, Duration.ofHours(1));
    AgentContext alice = context("alice", "trip-a");
    new H2AgentMemoryStore(
            database.toString(), Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC))
        .append(alice, "question", "answer", config);

    var expired =
        new H2AgentMemoryStore(
                database.toString(), Clock.fixed(Instant.ofEpochSecond(3_701), ZoneOffset.UTC))
            .load(alice, config);

    assertTrue(expired.isEmpty());
  }

  @Test
  void isolatesMemoryForSameIdentityAcrossRooms() {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);
    H2AgentMemoryStore store = new H2AgentMemoryStore(database.toString(), clock);
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

  @Test
  void wrapsLoadSqlFailuresWithoutDiscardingTheDatabaseCause() throws Exception {
    try (var connection = H2Database.open(database.toString());
        var statement = connection.createStatement()) {
      statement.executeUpdate("ALTER TABLE agent_memory RENAME COLUMN content TO content_bad");
    }
    H2AgentMemoryStore store =
        new H2AgentMemoryStore(
            database.toString(), Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC));

    AgentPersistenceException exception =
        assertThrows(
            AgentPersistenceException.class,
            () -> store.load(context("alice", "trip-a"), config(2, Duration.ofHours(1))));

    assertTrue(exception.getMessage().startsWith("Agent memory load failed"));
    assertTrue(exception.getCause() instanceof java.sql.SQLException);
  }

  @Test
  void wrapsAppendSqlFailuresWithoutDiscardingTheDatabaseCause() throws Exception {
    try (var connection = H2Database.open(database.toString());
        var statement = connection.createStatement()) {
      statement.executeUpdate("ALTER TABLE agent_memory RENAME COLUMN content TO content_bad");
    }

    AgentPersistenceException exception =
        assertThrows(
            AgentPersistenceException.class,
            () ->
                new H2AgentMemoryStore(
                        database.toString(),
                        Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC))
                    .append(
                        context("alice", "trip-a"),
                        "question",
                        "answer",
                        config(2, Duration.ofHours(1))));

    assertTrue(exception.getMessage().startsWith("Agent memory append failed"));
    assertTrue(exception.getCause() instanceof java.sql.SQLException);
  }

  @Test
  void wrapsToolEvidenceAppendFailuresWithoutDiscardingTheDatabaseCause() throws Exception {
    try (var connection = H2Database.open(database.toString());
        var statement = connection.createStatement()) {
      statement.executeUpdate("ALTER TABLE agent_tool_memory RENAME COLUMN content TO content_bad");
    }

    AgentPersistenceException exception =
        assertThrows(
            AgentPersistenceException.class,
            () ->
                new H2AgentMemoryStore(
                        database.toString(),
                        Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC))
                    .appendToolEvidence(
                        context("alice", "trip-a"),
                        "user_message_history",
                        "{}",
                        config(2, Duration.ofHours(1))));

    assertTrue(exception.getMessage().startsWith("Agent memory append tool evidence failed"));
    assertTrue(exception.getCause() instanceof java.sql.SQLException);
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
