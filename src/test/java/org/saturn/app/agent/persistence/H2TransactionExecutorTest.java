package org.saturn.app.agent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.persistence.H2Database;

class H2TransactionExecutorTest {
  @TempDir Path tempDir;
  private Path database;

  @BeforeEach
  void createDatabase() throws Exception {
    database = tempDir.resolve("transaction");
    H2Database.bootstrap(database.toString());
  }

  @Test
  void commitsWorkAndRestoresAutoCommitState() throws Exception {
    try (var connection = H2Database.open(database.toString())) {
      H2TransactionExecutor.execute(
          connection,
          transaction -> {
            try (PreparedStatement statement =
                transaction.prepareStatement(
                    "INSERT INTO agent_memory(identity_key, role, content, created_on, expires_on) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
              statement.setString(1, "transaction-success");
              statement.setString(2, "user");
              statement.setString(3, "committed");
              statement.setLong(4, 1);
              statement.setLong(5, 2);
              statement.executeUpdate();
            }
          });

      assertTrue(connection.getAutoCommit());
    }

    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement();
        var resultSet =
            statement.executeQuery(
                "SELECT COUNT(*) FROM agent_memory WHERE identity_key = 'transaction-success'")) {
      assertTrue(resultSet.next());
      assertEquals(1, resultSet.getInt(1));
    }
  }

  @Test
  void rollsBackFailedWorkAndRestoresAutoCommitState() throws Exception {
    try (var connection = H2Database.open(database.toString())) {
      assertThrows(
          SQLException.class,
          () ->
              H2TransactionExecutor.execute(
                  connection,
                  transaction -> {
                    try (Statement statement = transaction.createStatement()) {
                      statement.executeUpdate(
                          "INSERT INTO agent_memory(identity_key, role, content, created_on, expires_on) "
                              + "VALUES ('transaction-failure', 'user', 'rolled back', 1, 2)");
                      throw new SQLException("forced failure");
                    }
                  }));

      assertTrue(connection.getAutoCommit());
    }

    try (var connection = H2Database.open(database.toString());
        Statement statement = connection.createStatement();
        var resultSet =
            statement.executeQuery(
                "SELECT COUNT(*) FROM agent_memory WHERE identity_key = 'transaction-failure'")) {
      assertTrue(resultSet.next());
      assertEquals(0, resultSet.getInt(1));
    }
  }
}
