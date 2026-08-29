package org.saturn.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.model.Role;
import org.saturn.app.persistence.H2Database;

class AuthorizationServiceImplTest {
  @TempDir Path tempDir;
  private Connection connection;
  private AuthorizationServiceImpl service;

  @BeforeEach
  void createRoles() throws Exception {
    connection = H2Database.open(tempDir.resolve("roles").toString());
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO trips(type, trip, created_on) VALUES ('ADMIN', 'admin-trip', 1)");
    }
    service = new AuthorizationServiceImpl(connection, new ArrayBlockingQueue<>(4));
  }

  @AfterEach
  void closeConnection() throws Exception {
    connection.close();
  }

  @Test
  void resolvesPersistedRole() {
    assertEquals(Role.ADMIN, service.resolveRole("admin-trip"));
  }

  @Test
  void defaultsUnknownAndMissingTripsToRegular() {
    assertEquals(Role.REGULAR, service.resolveRole("unknown-trip"));
    assertEquals(Role.REGULAR, service.resolveRole(null));
  }
}
