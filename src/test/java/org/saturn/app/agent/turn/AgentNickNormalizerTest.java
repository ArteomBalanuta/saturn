package org.saturn.app.agent.turn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AgentNickNormalizerTest {
  @Test
  void normalizesNullWhitespaceEscapedUnderscoreAndMentionPrefix() {
    assertEquals("", AgentNickNormalizer.normalize(null));
    assertEquals("alice_bob", AgentNickNormalizer.normalize("  @alice\\_bob  "));
    assertEquals("alice", AgentNickNormalizer.normalize("alice"));
  }
}
