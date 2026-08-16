package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;

class AgentTurnMemoryTest {
  @Test
  void loadsHistoryWithoutLegacyPersonaTurns() throws Exception {
    AgentMemoryStore store =
        new AgentMemoryStore() {
          @Override
          public List<LlmMessage> load(AgentContext context, AgentConfig config) {
            return List.of(
                LlmMessage.user(
                    "Public Saturn message from @mer in #programming:\ntell me about jill"),
                LlmMessage.assistant(
                    "*[sips tea]*\nAh, merc. The archives reveal a user. Carpe diem.", List.of()),
                LlmMessage.user(
                    "Public Saturn message from @alice in #programming:\nwhere is lounge?"));
          }

          @Override
          public void append(
              AgentContext context,
              String userContent,
              String assistantContent,
              AgentConfig config) {}
        };
    AgentTurnMemory memory = new AgentTurnMemory(store, config());

    assertEquals(1, memory.load(context(), "request-1").size());
  }

  @Test
  void translatesMemoryAppendFailuresWithoutChangingPublicError() {
    RuntimeException failure = new RuntimeException("database detail");
    AgentMemoryStore store =
        new AgentMemoryStore() {
          @Override
          public List<LlmMessage> load(AgentContext context, AgentConfig config) {
            return List.of();
          }

          @Override
          public void append(
              AgentContext context,
              String userContent,
              String assistantContent,
              AgentConfig config) {
            throw failure;
          }
        };
    AgentTurnMemory memory = new AgentTurnMemory(store, config());

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () -> memory.append(context(), "question", "answer", "request-2"));

    assertEquals("Agent memory persistence failed", exception.getMessage());
    assertEquals(failure, exception.getCause());
  }

  private static AgentConfig config() {
    return AgentConfig.from(null, Map.of());
  }

  private static AgentContext context() {
    return new AgentContext("programming", "alice", "trip", "hash", false, List.of("alice"));
  }
}
