package org.saturn.app.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/** Creates and opens Saturn's embedded H2 database from a configured database stem. */
public final class H2Database {
  private static final String DRIVER_CLASS_NAME = "org.h2.Driver";
  private static final String URL_OPTIONS =
      ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";

  private H2Database() {}

  /**
   * Returns the file-mode H2 JDBC URL for {@code databaseStem}. H2 stores the data in {@code
   * <databaseStem>.mv.db}.
   */
  public static String jdbcUrl(String databaseStem) {
    return "jdbc:h2:file:" + absoluteStem(databaseStem) + URL_OPTIONS;
  }

  /** Returns an H2 URL optimized for an agent connection that only performs validated reads. */
  public static String readOnlyJdbcUrl(String databaseStem) {
    return jdbcUrl(databaseStem) + ";ACCESS_MODE_DATA=r";
  }

  /** Opens a configured H2 connection, creating the schema on first use. */
  public static Connection open(String databaseStem) throws SQLException {
    bootstrap(databaseStem);
    return connect(jdbcUrl(databaseStem));
  }

  /** Opens a configured read-only H2 connection for validated query workloads. */
  public static Connection openReadOnly(String databaseStem) throws SQLException {
    bootstrap(databaseStem);
    Connection connection = connect(readOnlyJdbcUrl(databaseStem));
    connection.setReadOnly(true);
    return connection;
  }

  /** Creates the database directory and fresh Saturn schema when no H2 database exists yet. */
  public static void bootstrap(String databaseStem) {
    Path stem = absoluteStem(databaseStem);
    try {
      Path parent = stem.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      if (SqliteToH2Migrator.migrateIfNeeded(stem)) {
        return;
      }
      try (Connection connection = connect(jdbcUrl(stem.toString()))) {
        H2SchemaBootstrapper.apply(connection);
      }
    } catch (IOException | SQLException exception) {
      throw new IllegalStateException("Unable to bootstrap H2 database at " + stem, exception);
    }
  }

  /** Returns the normalized absolute H2 file stem without the generated {@code .mv.db} suffix. */
  public static Path absoluteStem(String databaseStem) {
    Objects.requireNonNull(databaseStem, "databaseStem");
    if (databaseStem.isBlank()) {
      throw new IllegalArgumentException("databaseStem must not be blank");
    }
    String normalized =
        databaseStem.endsWith(".mv.db")
            ? databaseStem.substring(0, databaseStem.length() - ".mv.db".length())
            : databaseStem.endsWith(".db")
                ? databaseStem.substring(0, databaseStem.length() - ".db".length())
                : databaseStem;
    return Path.of(normalized).toAbsolutePath().normalize();
  }

  private static Connection connect(String jdbcUrl) throws SQLException {
    ensureDriverAvailable();
    return DriverManager.getConnection(jdbcUrl);
  }

  /**
   * Loads H2 explicitly because shaded JARs do not reliably retain JDBC service-loader metadata.
   */
  private static void ensureDriverAvailable() throws SQLException {
    try {
      Class.forName(DRIVER_CLASS_NAME);
    } catch (ClassNotFoundException exception) {
      throw new SQLException("H2 JDBC driver is not available on the runtime classpath", exception);
    }
  }
}
