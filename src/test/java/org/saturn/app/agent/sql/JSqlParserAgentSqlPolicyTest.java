package org.saturn.app.agent.sql;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.AgentSqlConfig;
import org.saturn.app.agent.persistence.AgentDatabaseSchema;

class JSqlParserAgentSqlPolicyTest {
  private AgentDatabaseSchema schema;
  private AgentSqlPolicy policy;

  @BeforeEach
  void setUp() {
    schema =
        new AgentDatabaseSchema(
            List.of(table("messages"), table("trips"), table("names"), table("trip_names")));
    policy = new JSqlParserAgentSqlPolicy(config(1_000));
  }

  @Test
  void acceptsSingleReadOnlySelects() {
    List<String> queries =
        List.of(
            "SELECT count(*) FROM messages",
            "SELECT m.name, t.type FROM messages m JOIN trips t ON t.trip = m.trip",
            "SELECT * FROM messages WHERE trip IN (SELECT trip FROM trips)",
            "SELECT trip FROM messages UNION SELECT trip FROM trips",
            "WITH recent AS (SELECT * FROM messages) SELECT count(*) FROM recent",
            "WITH recent AS (SELECT * FROM messages), known AS (SELECT * FROM trips) "
                + "SELECT recent.trip FROM recent JOIN known ON known.trip = recent.trip",
            "SELECT 1");

    for (String sql : queries) {
      assertDoesNotThrow(() -> policy.validate(sql, schema), sql);
    }
  }

  @Test
  void rejectsAnythingOtherThanOneSelect() {
    List<String> statements =
        List.of(
            "INSERT INTO messages(message) VALUES ('x')",
            "UPDATE messages SET message = 'x'",
            "DELETE FROM messages",
            "CREATE TABLE secrets(value TEXT)",
            "DROP TABLE messages",
            "PRAGMA table_info(messages)",
            "ATTACH DATABASE '/tmp/other.db' AS other",
            "DETACH DATABASE other",
            "VACUUM");

    for (String sql : statements) {
      AgentSqlPolicyException exception =
          assertThrows(AgentSqlPolicyException.class, () -> policy.validate(sql, schema), sql);
      assertEquals(AgentSqlErrorCode.FORBIDDEN_STATEMENT, exception.code(), sql);
    }
  }

  @Test
  void rejectsInternalUnknownAndDangerousReferences() {
    List<RejectedSql> queries =
        List.of(
            new RejectedSql(
                "SELECT * FROM information_schema.tables", AgentSqlErrorCode.FORBIDDEN_TABLE),
            new RejectedSql("SELECT * FROM missing_table", AgentSqlErrorCode.FORBIDDEN_TABLE),
            new RejectedSql(
                "SELECT load_extension('/tmp/evil')", AgentSqlErrorCode.FORBIDDEN_FUNCTION),
            new RejectedSql(
                "SELECT * FROM pragma_table_info('messages')",
                AgentSqlErrorCode.FORBIDDEN_FUNCTION));

    for (RejectedSql rejected : queries) {
      AgentSqlPolicyException exception =
          assertThrows(
              AgentSqlPolicyException.class,
              () -> policy.validate(rejected.sql(), schema),
              rejected.sql());
      assertEquals(rejected.code(), exception.code(), rejected.sql());
    }
  }

  @Test
  void rejectsMultipleStatementsEvenWhenBothAreSelects() {
    AgentSqlPolicyException exception =
        assertThrows(
            AgentSqlPolicyException.class,
            () -> policy.validate("SELECT * FROM messages; SELECT * FROM trips", schema));

    assertEquals(AgentSqlErrorCode.FORBIDDEN_STATEMENT, exception.code());
  }

  @Test
  void distinguishesBlankMalformedAndOverlongSql() {
    assertEquals(
        AgentSqlErrorCode.EMPTY_SQL,
        assertThrows(AgentSqlPolicyException.class, () -> policy.validate("  ", schema)).code());
    assertEquals(
        AgentSqlErrorCode.MALFORMED_SQL,
        assertThrows(
                AgentSqlPolicyException.class,
                () -> policy.validate("SELECT FROM messages", schema))
            .code());
    AgentSqlPolicy shortPolicy = new JSqlParserAgentSqlPolicy(config(10));
    assertEquals(
        AgentSqlErrorCode.SQL_TOO_LONG,
        assertThrows(
                AgentSqlPolicyException.class,
                () -> shortPolicy.validate("SELECT * FROM messages", schema))
            .code());
  }

  @Test
  void returnsStableSha256FingerprintWithoutChangingSql() {
    String sql = "SELECT * FROM messages";

    ValidatedAgentSql first = policy.validate(sql, schema);
    ValidatedAgentSql second = policy.validate(sql, schema);
    ValidatedAgentSql different = policy.validate("SELECT * FROM trips", schema);

    assertEquals(sql, first.sql());
    assertEquals(64, first.fingerprint().length());
    assertTrue(first.fingerprint().matches("[0-9a-f]{64}"));
    assertEquals(first.fingerprint(), second.fingerprint());
    assertNotEquals(first.fingerprint(), different.fingerprint());
  }

  private static AgentDatabaseSchema.Table table(String name) {
    return new AgentDatabaseSchema.Table(name, List.of(), List.of(), List.of());
  }

  private static AgentSqlConfig config(int maxSqlChars) {
    return new AgentSqlConfig(true, maxSqlChars, 50, 32, 2_000, 32_000, Duration.ofSeconds(1));
  }

  private record RejectedSql(String sql, AgentSqlErrorCode code) {}
}
