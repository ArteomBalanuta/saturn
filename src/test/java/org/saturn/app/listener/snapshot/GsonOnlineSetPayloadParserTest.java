package org.saturn.app.listener.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.facade.EngineType;

class GsonOnlineSetPayloadParserTest {
  private final OnlineSetPayloadParser roomParser =
      new GsonOnlineSetPayloadParser(EngineType.LIST_CMD, "workflow-1", "room");
  private final OnlineSetPayloadParser agentParser =
      new GsonOnlineSetPayloadParser(EngineType.AGENT, "workflow-2", "agent-room");

  @Test
  void decodesRoomUsersAsAnImmutableSnapshot() throws Exception {
    OnlineSetSnapshot snapshot =
        roomParser.parse("{\"cmd\":\"onlineSet\",\"users\":[{\"nick\":\"Alice\"}]}");

    assertEquals("Alice", snapshot.users().getFirst().getNick());
    assertEquals(false, snapshot.agentShape());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.users().add(null));
  }

  @Test
  void decodesAgentNicks() throws Exception {
    OnlineSetSnapshot snapshot =
        agentParser.parse("{\"cmd\":\"onlineSet\",\"nicks\":[\"Alice\",\"Bob\"]}");

    assertEquals(List.of("Alice", "Bob"), snapshot.users().stream().map(u -> u.getNick()).toList());
    assertTrue(snapshot.agentShape());
  }

  @Test
  void preservesAnEmptyArray() throws Exception {
    assertTrue(roomParser.parse("{\"cmd\":\"onlineSet\",\"users\":[]}").users().isEmpty());
  }

  @Test
  void rejectsMalformedAndInvalidPayloadsWithWorkflowContext() {
    for (String payload :
        List.of(
            "not-json",
            "{\"cmd\":\"chat\",\"users\":[]}",
            "{\"cmd\":\"onlineSet\"}",
            "{\"cmd\":\"onlineSet\",\"users\":null}",
            "{\"cmd\":\"onlineSet\",\"users\":[{\"nick\":\" \"}]}",
            "{\"cmd\":\"onlineSet\",\"users\":[3]}")) {
      PayloadDecodeException exception =
          assertThrows(PayloadDecodeException.class, () -> roomParser.parse(payload));
      assertTrue(exception.getMessage().contains("workflow-1"));
      assertTrue(exception.getMessage().contains("room"));
    }
  }
}
