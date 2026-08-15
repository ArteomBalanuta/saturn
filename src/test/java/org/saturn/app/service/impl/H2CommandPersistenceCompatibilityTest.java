package org.saturn.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.model.MessageAuditEvent;
import org.saturn.app.model.Role;
import org.saturn.app.persistence.H2Database;

/** Exercises shared persistence used by command dispatch against Saturn's production H2 dialect. */
class H2CommandPersistenceCompatibilityTest {
  @TempDir Path tempDir;

  @Test
  void registersAndReadsUsersWithMillisecondTimestamps() throws Exception {
    Path databaseStem = tempDir.resolve("saturn");
    H2Database.bootstrap(databaseStem.toString());

    try (var connection = H2Database.open(databaseStem.toString());
        var timestamps =
            connection.prepareStatement("SELECT created_on FROM names WHERE name = ?")) {
      var service = new UserServiceImpl(connection, new LinkedBlockingQueue<>());
      var logRepository = new LogRepositoryImpl(connection);

      assertEquals(0, service.register("Alice", "trip-a", "REGULAR"));
      assertTrue(service.isNameRegistered("ALICE"));
      assertTrue(service.isTripRegistered("TRIP-A"));

      timestamps.setString(1, "Alice");
      try (ResultSet resultSet = timestamps.executeQuery()) {
        assertTrue(resultSet.next());
        assertTrue(resultSet.getLong("created_on") > 1_000_000_000_000L);
      }

      logRepository.logMessage(
          MessageAuditEvent.publicMessage(
              "trip-a", "Alice", "hash-a", "hello", "programming", 1_700_000_000_000L));

      assertEquals(1, service.lastMessages("Alice", "trip-a", 1).size());
    }
  }

  @Test
  void persistsEveryApplicationRoleUsedByAuthorizationCommands() throws Exception {
    Path databaseStem = tempDir.resolve("roles");
    H2Database.bootstrap(databaseStem.toString());
    replaceTripTypeConstraintWithLegacyConstraint(databaseStem);
    H2Database.bootstrap(databaseStem.toString());

    try (var connection = H2Database.open(databaseStem.toString())) {
      var authorizationService =
          new AuthorizationServiceImpl(connection, new LinkedBlockingQueue<>());

      authorizationService.grant("trip-pest", Role.PEST);

      assertEquals(Role.PEST, authorizationService.resolveRole("trip-pest"));
    }
  }

  private static void replaceTripTypeConstraintWithLegacyConstraint(Path databaseStem)
      throws Exception {
    try (var connection = H2Database.open(databaseStem.toString());
        var findConstraint =
            connection.prepareStatement(
                """
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE LOWER(table_name) = 'trips' AND constraint_type = 'CHECK'
                """);
        var resultSet = findConstraint.executeQuery();
        var statement = connection.createStatement()) {
      assertTrue(resultSet.next());
      statement.execute("ALTER TABLE trips DROP CONSTRAINT \"" + resultSet.getString(1) + "\"");
      statement.execute(
          """
          ALTER TABLE trips ADD CONSTRAINT legacy_trips_type_check
          CHECK (type IN ('ADMIN', 'MODERATOR', 'TRUSTED', 'USER', 'REGULAR'))
          """);
    }
  }
}
