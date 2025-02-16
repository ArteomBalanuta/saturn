package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LastMessagesCommandImplTest {
  @Test
  public void getFrontCharacters() {
    String actual = LastMessagesCommandImpl.getFrontCharacters("abcdefgh", 5);
    assertEquals("abcde...", actual);
    String second = LastMessagesCommandImpl.getFrontCharacters("abcdefgh", 1);
    assertEquals("a...", second);
  }
}
