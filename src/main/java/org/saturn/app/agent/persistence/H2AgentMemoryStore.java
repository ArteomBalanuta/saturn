package org.saturn.app.agent.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentMemoryStore;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.persistence.H2Database;

/** Stores agent conversation memory in the H2-backed persistence layer. */
public final class H2AgentMemoryStore implements AgentMemoryStore {
  private final String databasePath;
  private final Clock clock;

  /**
   * Implements the {@code H2AgentMemoryStore} operation for this agent component.
   *
   * @param databasePath input argument used by this operation
   */
  public H2AgentMemoryStore(String databasePath) {
    this(databasePath, Clock.systemUTC());
  }

  /**
   * Implements the {@code H2AgentMemoryStore} operation for this agent component.
   *
   * @param databasePath input argument used by this operation
   * @param clock input argument used by this operation
   */
  public H2AgentMemoryStore(String databasePath, Clock clock) {
    this.databasePath = databasePath;
    this.clock = clock;
  }

  /**
   * Implements the {@code load} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @param config input argument used by this operation
   * @return the operation result
   */
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
        PreparedStatement statement = connection.prepareStatement(sql)) {
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

  /**
   * Implements the {@code loadToolEvidence} operation for this agent component.
   *
   * @param connection input argument used by this operation
   * @param context input argument used by this operation
   * @param config input argument used by this operation
   * @param now input argument used by this operation
   * @return the operation result
   */
  private List<LlmMessage> loadToolEvidence(
      Connection connection, AgentContext context, AgentConfig config, long now)
      throws SQLException {
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
              LlmMessage.system(
                  "[Internal tool evidence from %s]\n%s"
                      .formatted(
                          resultSet.getString("tool_name"), resultSet.getString("content"))));
        }
      }
      Collections.reverse(evidence);
      return evidence;
    }
  }

  /**
   * Implements the {@code append} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @param userContent input argument used by this operation
   * @param assistantContent input argument used by this operation
   * @param config input argument used by this operation
   */
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
      H2TransactionExecutor.execute(
          connection,
          transaction -> {
            try (PreparedStatement cleanup =
                    transaction.prepareStatement("DELETE FROM agent_memory WHERE expires_on <= ?");
                PreparedStatement statement = transaction.prepareStatement(sql)) {
              cleanup.setLong(1, createdOn);
              cleanup.executeUpdate();
              insert(statement, context.memoryKey(), "user", userContent, createdOn, expiresOn);
              insert(
                  statement,
                  context.memoryKey(),
                  "assistant",
                  assistantContent,
                  createdOn,
                  expiresOn);
            }
          });
    } catch (SQLException exception) {
      throw persistenceFailure("append", exception);
    }
  }

  /**
   * Implements the {@code appendToolEvidence} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @param toolName input argument used by this operation
   * @param content input argument used by this operation
   * @param config input argument used by this operation
   */
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
    try (Connection connection = open()) {
      H2TransactionExecutor.execute(
          connection,
          transaction -> {
            try (PreparedStatement cleanup =
                    transaction.prepareStatement(
                        "DELETE FROM agent_tool_memory WHERE expires_on <= ?");
                PreparedStatement statement = transaction.prepareStatement(sql)) {
              cleanup.setLong(1, createdOn);
              cleanup.executeUpdate();
              statement.setString(1, context.memoryKey());
              statement.setString(2, toolName);
              statement.setString(3, content);
              statement.setLong(4, createdOn);
              statement.setLong(5, expiresOn);
              statement.executeUpdate();
            }
          });
    } catch (SQLException exception) {
      throw persistenceFailure("append tool evidence", exception);
    }
  }

  /**
   * Implements the {@code open} operation for this agent component.
   *
   * @return the operation result
   */
  private Connection open() throws SQLException {
    return H2Database.open(databasePath);
  }

  /**
   * Implements the {@code insert} operation for this agent component.
   *
   * @param statement input argument used by this operation
   * @param identity input argument used by this operation
   * @param role input argument used by this operation
   * @param content input argument used by this operation
   * @param createdOn input argument used by this operation
   * @param expiresOn input argument used by this operation
   */
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

  /**
   * Implements the {@code persistenceFailure} operation for this agent component.
   *
   * @param operation input argument used by this operation
   * @param exception input argument used by this operation
   * @return the operation result
   */
  private static AgentPersistenceException persistenceFailure(
      String operation, SQLException exception) {
    String detail = exception.getMessage() == null ? "unknown H2 error" : exception.getMessage();
    return new AgentPersistenceException(
        "Agent memory %s failed, databaseCode=%d: %s"
            .formatted(operation, exception.getErrorCode(), detail),
        exception);
  }
}
