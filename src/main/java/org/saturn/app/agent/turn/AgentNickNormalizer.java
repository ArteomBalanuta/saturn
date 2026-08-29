package org.saturn.app.agent.turn;

/** Normalizes chat nick text before agent history lookups. */
public final class AgentNickNormalizer {
  /** Implements the {@code AgentNickNormalizer} operation for this agent component. */
  private AgentNickNormalizer() {}

  /**
   * Implements the {@code normalize} operation for this agent component.
   *
   * @param nick input argument used by this operation
   * @return the operation result
   */
  public static String normalize(String nick) {
    if (nick == null) {
      return "";
    }
    String normalized = nick.trim().replace("\\_", "_");
    return normalized.startsWith("@") ? normalized.substring(1) : normalized;
  }
}
