package org.saturn.app.listener.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InfoMessageListenerImplTest {

  @Test
  void testSubstring() {
    String room = "x was banished to ?lololo";

    InfoMessageListenerImpl infoMessageListener = new InfoMessageListenerImpl(null);
    String actual = infoMessageListener.substringFromEndUpTo(room, "?");
    assertEquals("lololo", actual);

    String name = "xn was banished to ?lololo2";
    String actualName = infoMessageListener.substringFromStartUpTo(name, " was ");
    assertEquals("xn", actualName);
  }
}
