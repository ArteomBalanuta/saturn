package org.saturn.app.agent.routing;

/** Unicode-safe text length and truncation helpers for provider request limits. */
final class AgentTextBounds {
  /** Implements the {@code AgentTextBounds} operation for this agent component. */
  private AgentTextBounds() {}

  /**
   * Implements the {@code codePointCount} operation for this agent component.
   *
   * @param value input argument used by this operation
   * @return the operation result
   */
  static int codePointCount(String value) {
    return value == null ? 0 : value.codePointCount(0, value.length());
  }

  /**
   * Implements the {@code truncate} operation for this agent component.
   *
   * @param value input argument used by this operation
   * @param maxCodePoints input argument used by this operation
   * @return the operation result
   */
  static String truncate(String value, int maxCodePoints) {
    if (value == null || codePointCount(value) <= maxCodePoints) {
      return value == null ? "" : value;
    }
    return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
  }
}
