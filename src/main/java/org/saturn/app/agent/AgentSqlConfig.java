package org.saturn.app.agent;

import com.moandjiezana.toml.Toml;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Immutable, bounded configuration for the optional dynamic read-only SQL capability. */
public record AgentSqlConfig(
    boolean enabled,
    int maxSqlChars,
    int maxRows,
    int maxColumns,
    int maxCellChars,
    int maxResultChars,
    Duration timeout) {
  public AgentSqlConfig {
    Objects.requireNonNull(timeout, "timeout");
    requirePositive(maxSqlChars, "dynamicSqlMaxSqlChars");
    requirePositive(maxRows, "dynamicSqlMaxRows");
    requirePositive(maxColumns, "dynamicSqlMaxColumns");
    requirePositive(maxCellChars, "dynamicSqlMaxCellChars");
    requirePositive(maxResultChars, "dynamicSqlMaxResultChars");
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("agent.dynamicSqlTimeoutMillis must be positive");
    }
  }

  public static AgentSqlConfig from(Toml root) {
    return from(root, Map.of());
  }

  /**
   * Loads dynamic-SQL bounds from TOML, with explicit {@code SATURN_AGENT_DYNAMIC_SQL_*}
   * environment values taking precedence.
   */
  public static AgentSqlConfig from(Toml root, Map<String, String> environment) {
    Toml table = root == null ? null : root.getTable("agent");
    return new AgentSqlConfig(
        readBoolean(
            table, environment, "dynamicSqlEnabled", "SATURN_AGENT_DYNAMIC_SQL_ENABLED", true),
        toInt(
            readLong(
                table,
                environment,
                "dynamicSqlMaxSqlChars",
                "SATURN_AGENT_DYNAMIC_SQL_MAX_SQL_CHARS",
                4_000),
            "dynamicSqlMaxSqlChars"),
        toInt(
            readLong(
                table, environment, "dynamicSqlMaxRows", "SATURN_AGENT_DYNAMIC_SQL_MAX_ROWS", 50),
            "dynamicSqlMaxRows"),
        toInt(
            readLong(
                table,
                environment,
                "dynamicSqlMaxColumns",
                "SATURN_AGENT_DYNAMIC_SQL_MAX_COLUMNS",
                32),
            "dynamicSqlMaxColumns"),
        toInt(
            readLong(
                table,
                environment,
                "dynamicSqlMaxCellChars",
                "SATURN_AGENT_DYNAMIC_SQL_MAX_CELL_CHARS",
                2_000),
            "dynamicSqlMaxCellChars"),
        toInt(
            readLong(
                table,
                environment,
                "dynamicSqlMaxResultChars",
                "SATURN_AGENT_DYNAMIC_SQL_MAX_RESULT_CHARS",
                32_000),
            "dynamicSqlMaxResultChars"),
        Duration.ofMillis(
            readLong(
                table,
                environment,
                "dynamicSqlTimeoutMillis",
                "SATURN_AGENT_DYNAMIC_SQL_TIMEOUT_MILLIS",
                1_000)));
  }

  private static long readLong(Toml table, String key, long fallback) {
    Long value = table == null ? null : table.getLong(key);
    return value == null ? fallback : value;
  }

  private static long readLong(
      Toml table,
      Map<String, String> environment,
      String key,
      String environmentKey,
      long fallback) {
    String environmentValue = environment.get(environmentKey);
    if (environmentValue == null || environmentValue.isBlank()) {
      return readLong(table, key, fallback);
    }
    try {
      return Long.parseLong(environmentValue.strip());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(environmentKey + " must be an integer", exception);
    }
  }

  private static boolean readBoolean(Toml table, String key, boolean fallback) {
    Boolean value = table == null ? null : table.getBoolean(key);
    return value == null ? fallback : value;
  }

  private static boolean readBoolean(
      Toml table,
      Map<String, String> environment,
      String key,
      String environmentKey,
      boolean fallback) {
    String environmentValue = environment.get(environmentKey);
    if (environmentValue == null || environmentValue.isBlank()) {
      return readBoolean(table, key, fallback);
    }
    if ("true".equalsIgnoreCase(environmentValue.strip())) {
      return true;
    }
    if ("false".equalsIgnoreCase(environmentValue.strip())) {
      return false;
    }
    throw new IllegalArgumentException(environmentKey + " must be true or false");
  }

  private static int toInt(long value, String key) {
    try {
      return Math.toIntExact(value);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("agent." + key + " is outside integer range", exception);
    }
  }

  private static void requirePositive(int value, String key) {
    if (value <= 0) {
      throw new IllegalArgumentException("agent." + key + " must be positive");
    }
  }
}
