package org.saturn.app.agent.config;

import com.moandjiezana.toml.Toml;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, bounded configuration for the optional dynamic read-only SQL capability.
 *
 * @param enabled whether dynamic SQL is available
 * @param maxSqlChars maximum accepted SQL length
 * @param maxRows maximum returned rows
 * @param maxColumns maximum returned columns
 * @param maxCellChars maximum serialized cell length
 * @param maxResultChars maximum serialized result length
 * @param timeout query execution timeout
 */
public record AgentSqlConfig(
    boolean enabled,
    int maxSqlChars,
    int maxRows,
    int maxColumns,
    int maxCellChars,
    int maxResultChars,
    Duration timeout) {
  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param enabled the enabled input; null handling follows the validation performed by this
   *     declaration
   * @param maxSqlChars the maxSqlChars input; null handling follows the validation performed by
   *     this declaration
   * @param maxRows the maxRows input; null handling follows the validation performed by this
   *     declaration
   * @param maxColumns the maxColumns input; null handling follows the validation performed by this
   *     declaration
   * @param maxCellChars the maxCellChars input; null handling follows the validation performed by
   *     this declaration
   * @param maxResultChars the maxResultChars input; null handling follows the validation performed
   *     by this declaration
   * @param timeout the timeout input; null handling follows the validation performed by this
   *     declaration
   */
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

  /**
   * Implements the {@code from} operation for this agent component.
   *
   * @param root input argument used by this operation
   * @return the operation result
   */
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
        AgentConfigValueReader.readBoolean(
            table, environment, "dynamicSqlEnabled", "SATURN_AGENT_DYNAMIC_SQL_ENABLED", true),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table,
                environment,
                "dynamicSqlMaxSqlChars",
                "SATURN_AGENT_DYNAMIC_SQL_MAX_SQL_CHARS",
                4_000),
            "dynamicSqlMaxSqlChars"),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table, environment, "dynamicSqlMaxRows", "SATURN_AGENT_DYNAMIC_SQL_MAX_ROWS", 50),
            "dynamicSqlMaxRows"),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table,
                environment,
                "dynamicSqlMaxColumns",
                "SATURN_AGENT_DYNAMIC_SQL_MAX_COLUMNS",
                32),
            "dynamicSqlMaxColumns"),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table,
                environment,
                "dynamicSqlMaxCellChars",
                "SATURN_AGENT_DYNAMIC_SQL_MAX_CELL_CHARS",
                2_000),
            "dynamicSqlMaxCellChars"),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table,
                environment,
                "dynamicSqlMaxResultChars",
                "SATURN_AGENT_DYNAMIC_SQL_MAX_RESULT_CHARS",
                32_000),
            "dynamicSqlMaxResultChars"),
        Duration.ofMillis(
            AgentConfigValueReader.readLong(
                table,
                environment,
                "dynamicSqlTimeoutMillis",
                "SATURN_AGENT_DYNAMIC_SQL_TIMEOUT_MILLIS",
                1_000)));
  }

  /**
   * Implements the {@code requirePositive} operation for this agent component.
   *
   * @param value input argument used by this operation
   * @param key input argument used by this operation
   */
  private static void requirePositive(int value, String key) {
    if (value <= 0) {
      throw new IllegalArgumentException("agent." + key + " must be positive");
    }
  }
}
