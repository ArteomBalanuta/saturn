package org.saturn.app.agent.config;

import com.moandjiezana.toml.Toml;
import java.util.Map;

/** Shared, strict scalar readers for agent configuration sources. */
public final class AgentConfigValueReader {
  /** Implements the {@code AgentConfigValueReader} operation for this agent component. */
  private AgentConfigValueReader() {}

  /**
   * Implements the {@code readString} operation for this agent component.
   *
   * @param table input argument used by this operation
   * @param key input argument used by this operation
   * @param fallback input argument used by this operation
   * @return the operation result
   */
  public static String readString(Toml table, String key, String fallback) {
    String value = table == null ? null : table.getString(key);
    return value == null ? fallback : value;
  }

  /**
   * Implements the {@code readString} operation for this agent component.
   *
   * @param table input argument used by this operation
   * @param environment input argument used by this operation
   * @param key input argument used by this operation
   * @param environmentKey input argument used by this operation
   * @param fallback input argument used by this operation
   * @return the operation result
   */
  public static String readString(
      Toml table,
      Map<String, String> environment,
      String key,
      String environmentKey,
      String fallback) {
    String environmentValue = environment.get(environmentKey);
    return environmentValue == null || environmentValue.isBlank()
        ? readString(table, key, fallback)
        : environmentValue;
  }

  /**
   * Implements the {@code readLong} operation for this agent component.
   *
   * @param table input argument used by this operation
   * @param key input argument used by this operation
   * @param fallback input argument used by this operation
   * @return the operation result
   */
  public static long readLong(Toml table, String key, long fallback) {
    Long value = table == null ? null : table.getLong(key);
    return value == null ? fallback : value;
  }

  /**
   * Implements the {@code readLong} operation for this agent component.
   *
   * @param table input argument used by this operation
   * @param environment input argument used by this operation
   * @param key input argument used by this operation
   * @param environmentKey input argument used by this operation
   * @param fallback input argument used by this operation
   * @return the operation result
   */
  public static long readLong(
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

  /**
   * Implements the {@code readBoolean} operation for this agent component.
   *
   * @param table input argument used by this operation
   * @param key input argument used by this operation
   * @param fallback input argument used by this operation
   * @return the operation result
   */
  public static boolean readBoolean(Toml table, String key, boolean fallback) {
    Boolean value = table == null ? null : table.getBoolean(key);
    return value == null ? fallback : value;
  }

  /**
   * Implements the {@code readBoolean} operation for this agent component.
   *
   * @param table input argument used by this operation
   * @param environment input argument used by this operation
   * @param key input argument used by this operation
   * @param environmentKey input argument used by this operation
   * @param fallback input argument used by this operation
   * @return the operation result
   */
  public static boolean readBoolean(
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

  /**
   * Implements the {@code toInt} operation for this agent component.
   *
   * @param value input argument used by this operation
   * @param key input argument used by this operation
   * @return the operation result
   */
  public static int toInt(long value, String key) {
    try {
      return Math.toIntExact(value);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("agent." + key + " is outside integer range", exception);
    }
  }

  /**
   * Implements the {@code requirePositive} operation for this agent component.
   *
   * @param value input argument used by this operation
   * @param key input argument used by this operation
   */
  public static void requirePositive(long value, String key) {
    if (value <= 0) {
      throw new IllegalArgumentException("agent." + key + " must be positive");
    }
  }
}
