package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentMentionParserTest {
  private final AgentMentionParser parser = new AgentMentionParser();

  @Test
  void parsesAnExactCaseInsensitiveMentionAndStripsAddressPunctuation() {
    assertEquals(
        "can you explain this?",
        parser.parse("@KoRiN, can you explain this?", "korin").orElseThrow());
    assertEquals(
        "what do you think?", parser.parse("what do you think, @KORIN?", "korin").orElseThrow());
  }

  @Test
  void rejectsPartialMentionsAndEmptyBodies() {
    assertTrue(parser.parse("@korin-helper explain this", "korin").isEmpty());
    assertTrue(parser.parse("email@korin.example", "korin").isEmpty());
    assertTrue(parser.parse("@korin", "korin").isEmpty());
  }
}
