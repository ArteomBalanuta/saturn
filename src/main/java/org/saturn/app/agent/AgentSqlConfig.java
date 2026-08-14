package org.saturn.app.agent;

import com.moandjiezana.toml.Toml;
import java.time.Duration;
import java.util.Objects;

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
    Toml table = root == null ? null : root.getTable("agent");
    return new AgentSqlConfig(
        readBoolean(table, "dynamicSqlEnabled", true),
        toInt(readLong(table, "dynamicSqlMaxSqlChars", 4_000), "dynamicSqlMaxSqlChars"),
        toInt(readLong(table, "dynamicSqlMaxRows", 50), "dynamicSqlMaxRows"),
        toInt(readLong(table, "dynamicSqlMaxColumns", 32), "dynamicSqlMaxColumns"),
        toInt(readLong(table, "dynamicSqlMaxCellChars", 2_000), "dynamicSqlMaxCellChars"),
        toInt(readLong(table, "dynamicSqlMaxResultChars", 32_000), "dynamicSqlMaxResultChars"),
        Duration.ofMillis(readLong(table, "dynamicSqlTimeoutMillis", 1_000)));
  }

  private static long readLong(Toml table, String key, long fallback) {
    Long value = table == null ? null : table.getLong(key);
    return value == null ? fallback : value;
  }

  private static boolean readBoolean(Toml table, String key, boolean fallback) {
    Boolean value = table == null ? null : table.getBoolean(key);
    return value == null ? fallback : value;
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
