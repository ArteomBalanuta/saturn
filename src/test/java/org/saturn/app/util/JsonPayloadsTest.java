package org.saturn.app.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonPayloadsTest {
  @Test
  void commandEscapesJsonSensitiveValues() {
    String payload = JsonPayloads.command("ban", "nick", "mer\"c\\");

    assertEquals("{ \"cmd\": \"ban\", \"nick\": \"mer\\\"c\\\\\"}", payload);
  }

  @Test
  void commandEscapesMultipleValues() {
    String payload = JsonPayloads.command("kick", "nick", "me\\", "to", "x\"y");

    assertEquals("{ \"cmd\": \"kick\", \"nick\": \"me\\\\\", \"to\":\"x\\\"y\"}", payload);
  }
}
