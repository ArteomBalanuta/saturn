package org.saturn.app.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentToolArgumentReaderTest {
  @Test
  void readsTrimmedNonBlankStringArguments() {
    JsonObject arguments = new JsonObject();
    arguments.addProperty("room", "  lounge  ");

    assertEquals(Optional.of("lounge"), AgentToolArgumentReader.nonBlankString(arguments, "room"));
  }

  @Test
  void rejectsMissingNonStringAndBlankArguments() {
    JsonObject arguments = new JsonObject();
    arguments.addProperty("blank", "  ");
    arguments.addProperty("number", 7);

    assertTrue(AgentToolArgumentReader.nonBlankString(arguments, "missing").isEmpty());
    assertTrue(AgentToolArgumentReader.nonBlankString(arguments, "blank").isEmpty());
    assertTrue(AgentToolArgumentReader.nonBlankString(arguments, "number").isEmpty());
  }
}
