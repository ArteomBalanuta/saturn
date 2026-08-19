package org.saturn.app.agent.turn;

/** Normalizes chat nick text before agent history lookups. */
public final class AgentNickNormalizer {
  private AgentNickNormalizer() {}

  public static String normalize(String nick) {
    if (nick == null) {
      return "";
    }
    String normalized = nick.trim().replace("\\_", "_");
    return normalized.startsWith("@") ? normalized.substring(1) : normalized;
  }
}
