package org.saturn.app.agent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmToolCall;

class AgentMessageProjectorTest {
  @Test
  void keepsCompleteToolCallUnitsAndDropsOrphansWithoutMutatingInput() {
    LlmToolCall first = new LlmToolCall("first", "room_users", "{}");
    LlmToolCall second = new LlmToolCall("second", "weather", "{}");
    List<LlmMessage> source =
        List.of(
            LlmMessage.system("system"),
            LlmMessage.assistant("calls", List.of(first, second)),
            LlmMessage.tool("first", "one"),
            LlmMessage.tool("second", "two"),
            LlmMessage.tool("orphan", "bad"),
            LlmMessage.user("current"));

    AgentContextProjection projection = new AgentMessageProjector().project(source, 10_000);

    assertEquals("system", projection.messages().getFirst().role());
    assertEquals("current", projection.messages().getLast().content());
    assertTrue(projection.messages().stream().noneMatch(m -> "orphan".equals(m.toolCallId())));
    assertFalse(projection.fingerprint().isBlank());
    assertEquals(6, source.size());
  }
}
