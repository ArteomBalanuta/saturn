package org.saturn.app.agent.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.saturn.app.agent.AgentConfig;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentMemoryStore;
import org.saturn.app.agent.llm.LlmMessage;

public final class SqliteAgentMemoryStore implements AgentMemoryStore {
  private final String jdbcUrl;
  private final Clock clock;

  public SqliteAgentMemoryStore(String databasePath) {
    this(databasePath, Clock.systemUTC());
  }

  public SqliteAgentMemoryStore(String databasePath, Clock clock) {
    this.jdbcUrl = "jdbc:sqlite:" + databasePath;
    this.clock = clock;
  }

  @Override
  public List<LlmMessage> load(AgentContext context, AgentConfig config) {
    long now = clock.instant().getEpochSecond();
    String sql =
        """
        SELECT role, content
        FROM agent_memory
        WHERE identity_key = ? AND expires_on > ?
        ORDER BY created_on DESC, id DESC
        LIMIT ?
        """;
    try (Connection connection = open();
        PreparedStatement queryOnly = connection.prepareStatement("PRAGMA query_only = ON");
        PreparedStatement statement = connection.prepareStatement(sql)) {
      queryOnly.execute();
      statement.setString(1, context.memoryKey());
      statement.setLong(2, now);
      statement.setInt(3, config.memoryTurns() * 2);
      List<LlmMessage> result = new ArrayList<>();
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          String role = resultSet.getString("role");
          String content = resultSet.getString("content");
          result.add(
              "user".equals(role)
                  ? LlmMessage.user(content)
                  : LlmMessage.assistant(content, List.of()));
        }
      }
      Collections.reverse(result);
      result.addAll(loadToolEvidence(connection, context, config, now));
      return List.copyOf(result);
    } catch (SQLException exception) {
      throw persistenceFailure("load", exception);
    }
  }

  private List<LlmMessage> loadToolEvidence(
      Connection connection, AgentContext context, AgentConfig config, long now) throws SQLException {
    String sql =
        """
        SELECT tool_name, content
        FROM agent_tool_memory
        WHERE identity_key = ? AND expires_on > ?
        ORDER BY created_on DESC, id DESC
        LIMIT ?
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, context.memoryKey());
      statement.setLong(2, now);
      statement.setInt(3, config.memoryTurns());
      List<LlmMessage> evidence = new ArrayList<>();
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          evidence.add(
              LlmMessage.assistant(
                  "[Internal tool evidence from %s]\n%s"
                      .formatted(resultSet.getString("tool_name"), resultSet.getString("content")),
                  List.of()));
        }
      }
      Collections.reverse(evidence);
      return evidence;
    }
  }

  @Override
  public void append(
      AgentContext context, String userContent, String assistantContent, AgentConfig config) {
    long createdOn = clock.instant().getEpochSecond();
    long expiresOn = createdOn + config.memoryTtl().toSeconds();
    String sql =
        """
        INSERT INTO agent_memory(identity_key, role, content, created_on, expires_on)
        VALUES (?, ?, ?, ?, ?)
        """;
    try (Connection connection = open()) {
      connection.setAutoCommit(false);
      try (PreparedStatement cleanup =
              connection.prepareStatement("DELETE FROM agent_memory WHERE expires_on <= ?");
          PreparedStatement statement = connection.prepareStatement(sql)) {
        cleanup.setLong(1, createdOn);
        cleanup.executeUpdate();
        insert(statement, context.memoryKey(), "user", userContent, createdOn, expiresOn);
        insert(statement, context.memoryKey(), "assistant", assistantContent, createdOn, expiresOn);
      }
      connection.commit();
    } catch (SQLException exception) {
      throw persistenceFailure("append", exception);
    }
  }

  @Override
  public void appendToolEvidence(
      AgentContext context, String toolName, String content, AgentConfig config) {
    long createdOn = clock.instant().getEpochSecond();
    long expiresOn = createdOn + config.memoryTtl().toSeconds();
    String sql =
        """
        INSERT INTO agent_tool_memory(identity_key, tool_name, content, created_on, expires_on)
        VALUES (?, ?, ?, ?, ?)
        """;
    try (Connection connection = open();
        PreparedStatement cleanup =
            connection.prepareStatement("DELETE FROM agent_tool_memory WHERE expires_on <= ?");
        PreparedStatement statement = connection.prepareStatement(sql)) {
      connection.setAutoCommit(false);
      cleanup.setLong(1, createdOn);
      cleanup.executeUpdate();
      statement.setString(1, context.memoryKey());
      statement.setString(2, toolName);
      statement.setString(3, content);
      statement.setLong(4, createdOn);
      statement.setLong(5, expiresOn);
      statement.executeUpdate();
      connection.commit();
    } catch (SQLException exception) {
      throw persistenceFailure("append tool evidence", exception);
    }
  }

  private Connection open() throws SQLException {
    Connection connection = DriverManager.getConnection(jdbcUrl);
    try (PreparedStatement foreignKeys = connection.prepareStatement("PRAGMA foreign_keys = ON");
        PreparedStatement timeout = connection.prepareStatement("PRAGMA busy_timeout = 5000")) {
      foreignKeys.execute();
      timeout.execute();
    }
    return connection;
  }

  private static void insert(
      PreparedStatement statement,
      String identity,
      String role,
      String content,
      long createdOn,
      long expiresOn)
      throws SQLException {
    statement.setString(1, identity);
    statement.setString(2, role);
    statement.setString(3, content);
    statement.setLong(4, createdOn);
    statement.setLong(5, expiresOn);
    statement.executeUpdate();
  }

  private static AgentPersistenceException persistenceFailure(
      String operation, SQLException exception) {
    String detail =
        exception.getMessage() == null ? "unknown SQLite error" : exception.getMessage();
    return new AgentPersistenceException(
        "Agent memory %s failed, sqliteCode=%d: %s"
            .formatted(operation, exception.getErrorCode(), detail),
        exception);
  }
}
