package org.saturn.app.agent.routing;

/** Unicode-safe text length and truncation helpers for provider request limits. */
final class AgentTextBounds {
  private AgentTextBounds() {}

  static int codePointCount(String value) {
    return value == null ? 0 : value.codePointCount(0, value.length());
  }

  static String truncate(String value, int maxCodePoints) {
    if (value == null || codePointCount(value) <= maxCodePoints) {
      return value == null ? "" : value;
    }
    return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
  }
}
