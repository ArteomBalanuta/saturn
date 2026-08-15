package org.saturn.app.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** Applies Saturn's idempotent H2 schema resource to an embedded database connection. */
final class H2SchemaBootstrapper {
  private static final String SCHEMA_RESOURCE = "/schema-h2.sql";

  private H2SchemaBootstrapper() {}

  static void apply(Connection connection) throws SQLException {
    Objects.requireNonNull(connection, "connection");
    try (BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(
                    Objects.requireNonNull(
                        H2SchemaBootstrapper.class.getResourceAsStream(SCHEMA_RESOURCE),
                        "Missing " + SCHEMA_RESOURCE),
                    StandardCharsets.UTF_8));
        Statement statement = connection.createStatement()) {
      StringBuilder sql = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("--")) {
          continue;
        }
        sql.append(line).append('\n');
        if (trimmed.endsWith(";")) {
          statement.execute(sql.toString());
          sql.setLength(0);
        }
      }
    } catch (IOException exception) {
      throw new SQLException("Unable to load H2 schema resource", exception);
    }
  }
}
