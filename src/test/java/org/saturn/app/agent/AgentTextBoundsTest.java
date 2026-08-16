package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AgentTextBoundsTest {
  @Test
  void countsAndTruncatesUnicodeByCodePoint() {
    String value = "a😀b";

    assertEquals(3, AgentTextBounds.codePointCount(value));
    assertEquals("a😀", AgentTextBounds.truncate(value, 2));
  }

  @Test
  void treatsNullAsEmptyText() {
    assertEquals(0, AgentTextBounds.codePointCount(null));
    assertEquals("", AgentTextBounds.truncate(null, 10));
  }
}
