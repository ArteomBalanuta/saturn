package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.agent.config.AgentSqlConfig;
import org.saturn.app.agent.sql.AgentSqlErrorCode;
import org.saturn.app.agent.sql.ValidatedAgentSql;
import org.saturn.app.persistence.H2Database;

class H2AgentSqlRepositoryTest {
  @TempDir Path tempDir;
  private Path database;
  private H2AgentSqlRepository repository;

  @BeforeEach
  void createDatabase() throws Exception {
    database = tempDir.resolve("queries");
    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE samples (
            id INTEGER,
            real_value REAL,
            text_value TEXT,
            blob_value BLOB,
            null_value TEXT)
          """);
      statement.executeUpdate("INSERT INTO samples VALUES (7, 3.5, 'hello', X'00FF', NULL)");
      statement.executeUpdate("CREATE TABLE many_values (value INTEGER, text_value TEXT)");
    }
    try (var connection = H2Database.open(database.toString());
        PreparedStatement statement =
            connection.prepareStatement("INSERT INTO many_values VALUES (?, ?)")) {
      for (int index = 0; index < 100; index++) {
        statement.setInt(1, index);
        statement.setString(2, "row-" + index + "-abcdefghij");
        statement.addBatch();
      }
      statement.executeBatch();
    }
    repository = new H2AgentSqlRepository(new H2ReadOnlyConnectionFactory(database.toString()));
  }

  @Test
  void returnsColumnsAndJsonSafeH2Values() {
    AgentSqlResult result =
        repository.execute(
            sql("SELECT id, real_value, text_value, blob_value, null_value FROM samples"),
            config(50, 32, 2_000, 32_000, Duration.ofSeconds(1)));

    assertEquals(
        java.util.List.of("id", "real_value", "text_value", "blob_value", "null_value"),
        result.columns());
    assertEquals(1, result.rows().size());
    assertEquals(7L, result.rows().getFirst().get(0));
    assertEquals(3.5d, result.rows().getFirst().get(1));
    assertEquals("hello", result.rows().getFirst().get(2));
    assertEquals(
        Base64.getEncoder().encodeToString(new byte[] {0, (byte) 0xff}),
        result.rows().getFirst().get(3));
    assertNull(result.rows().getFirst().get(4));
  }

  @Test
  void capsRowsAndMarksResultAsTruncated() {
    AgentSqlResult result =
        repository.execute(
            sql("SELECT value FROM many_values ORDER BY value"),
            config(3, 32, 2_000, 32_000, Duration.ofSeconds(1)));

    assertEquals(3, result.rows().size());
    assertTrue(result.truncated());
    assertEquals(2L, result.rows().getLast().getFirst());
  }

  @Test
  void truncatesTextOnUnicodeCodePointBoundary() throws Exception {
    try (var connection = H2Database.open(database.toString());
        PreparedStatement statement =
            connection.prepareStatement("INSERT INTO samples VALUES (?, ?, ?, ?, ?)"); ) {
      statement.setInt(1, 8);
      statement.setDouble(2, 4.5);
      statement.setString(3, "A😀BC");
      statement.setBytes(4, new byte[] {1});
      statement.setNull(5, java.sql.Types.VARCHAR);
      statement.executeUpdate();
    }

    AgentSqlResult result =
        repository.execute(
            sql("SELECT text_value FROM samples WHERE id = 8"),
            config(50, 32, 3, 32_000, Duration.ofSeconds(1)));

    assertEquals("A😀B", result.rows().getFirst().getFirst());
    assertTrue(result.truncated());
  }

  @Test
  void boundsSerializedResultSize() {
    AgentSqlConfig config = config(50, 32, 2_000, 220, Duration.ofSeconds(1));

    AgentSqlResult result =
        repository.execute(sql("SELECT value, text_value FROM many_values ORDER BY value"), config);

    String json = new Gson().toJson(result);
    assertTrue(json.codePointCount(0, json.length()) <= config.maxResultChars());
    assertTrue(result.truncated());
    assertTrue(result.rows().size() < 50);
  }

  @Test
  void rejectsOversizedMetadataWhenTheQueryReturnsNoRows() {
    AgentPersistenceException exception =
        assertThrows(
            AgentPersistenceException.class,
            () ->
                repository.execute(
                    sql("SELECT id, real_value, text_value FROM samples WHERE false"),
                    config(50, 32, 2_000, 1, Duration.ofSeconds(1))));

    assertEquals(AgentSqlErrorCode.RESULT_TOO_LARGE, exception.code());
    assertEquals("Agent SQL metadata exceeds the result limit", exception.getMessage());
  }

  @Test
  void acceptsUnicodeSqlWithinConfiguredCharacterLimit() {
    String unicode = "😀".repeat(1_000);

    AgentSqlResult result =
        repository.execute(
            sql("SELECT '" + unicode + "'"), config(50, 32, 2_000, 32_000, Duration.ofSeconds(1)));

    assertEquals(unicode, result.rows().getFirst().getFirst());
  }

  @Test
  void acceptsValidFingerprintAndHandlesUnboundedRowsAndTimeoutDuration() throws Exception {
    AgentSqlConfig config =
        config(Integer.MAX_VALUE, 32, 2_000, 32_000, Duration.ofSeconds(Long.MAX_VALUE));

    AgentSqlResult result =
        repository.execute(
            new ValidatedAgentSql(
                "SELECT value FROM many_values ORDER BY value",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
            config);

    assertEquals(100, result.rows().size());
    assertEquals(99L, result.rows().getLast().getFirst());

    var method =
        H2AgentSqlRepository.class.getDeclaredMethod("queryTimeoutSeconds", Duration.class);
    method.setAccessible(true);
    assertEquals(
        Integer.MAX_VALUE / 1_000, method.invoke(repository, Duration.ofSeconds(Long.MAX_VALUE)));
  }

  @Test
  void rejectsResultsWithTooManyColumns() {
    AgentPersistenceException exception =
        assertThrows(
            AgentPersistenceException.class,
            () ->
                repository.execute(
                    sql("SELECT id, real_value, text_value FROM samples"),
                    config(50, 2, 2_000, 32_000, Duration.ofSeconds(1))));

    assertEquals(AgentSqlErrorCode.RESULT_TOO_LARGE, exception.code());
  }

  @Test
  void truncatesOversizedH2Values() {
    AgentSqlResult result =
        repository.execute(
            sql("SELECT REPEAT('x', 1000000)"),
            config(50, 32, 2_000, 32_000, Duration.ofSeconds(1)));

    assertEquals(2_000, ((String) result.rows().getFirst().getFirst()).length());
    assertTrue(result.truncated());
  }

  @Test
  void boundsDirectBinaryValuesAsTruncatedBase64() {
    AgentSqlResult result =
        repository.execute(
            sql("SELECT CAST(X'00FF10203040' AS VARBINARY)"),
            config(50, 32, 4, 32_000, Duration.ofSeconds(1)));

    assertEquals("AP8Q", result.rows().getFirst().getFirst());
    assertTrue(result.truncated());
  }

  @Test
  void normalizesNarrowIntegerTypesAndBoundsBase64ToTheCellLimit() {
    AgentSqlResult result =
        repository.execute(
            sql(
                "SELECT CAST(7 AS TINYINT), CAST(8 AS SMALLINT), "
                    + "CAST(X'00FF10203040' AS VARBINARY)"),
            config(50, 32, 1, 32_000, Duration.ofSeconds(1)));

    assertEquals(7L, result.rows().getFirst().get(0));
    assertEquals(8L, result.rows().getFirst().get(1));
    assertEquals("", result.rows().getFirst().get(2));
    assertTrue(result.truncated());
  }

  @Test
  void interruptsQueryAfterConfiguredDeadline() {
    String expensiveQuery =
        """
        WITH RECURSIVE counter(value) AS (
          SELECT 1
          UNION ALL
          SELECT value + 1 FROM counter WHERE value < 1000000000)
        SELECT sum(value) FROM counter
        """;

    AgentPersistenceException exception =
        assertThrows(
            AgentPersistenceException.class,
            () ->
                repository.execute(
                    sql(expensiveQuery), config(50, 32, 2_000, 32_000, Duration.ofMillis(1))));

    assertEquals(AgentSqlErrorCode.TIMEOUT, exception.code());
  }

  @Test
  void readOnlyConnectionRejectsWriteEvenWhenPolicyIsBypassed() throws Exception {
    AgentPersistenceException exception =
        assertThrows(
            AgentPersistenceException.class,
            () ->
                repository.execute(
                    sql("DELETE FROM samples"),
                    config(50, 32, 2_000, 32_000, Duration.ofSeconds(1))));

    assertEquals(AgentSqlErrorCode.EXECUTION_FAILED, exception.code());
    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement();
        var resultSet = statement.executeQuery("SELECT count(*) FROM samples")) {
      assertTrue(resultSet.next());
      assertEquals(1, resultSet.getInt(1));
    }
  }

  private static ValidatedAgentSql sql(String sql) {
    return new ValidatedAgentSql(sql, "test-fingerprint");
  }

  private static AgentSqlConfig config(
      int maxRows, int maxColumns, int maxCellChars, int maxResultChars, Duration timeout) {
    return new AgentSqlConfig(
        true, 4_000, maxRows, maxColumns, maxCellChars, maxResultChars, timeout);
  }
}
