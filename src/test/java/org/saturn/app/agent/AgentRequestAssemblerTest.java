package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;

class AgentRequestAssemblerTest {
  @Test
  void assemblesBoundedMessagesAndModeSpecificTools() {
    AgentToolRegistry registry =
        new AgentToolRegistry().register(tool("run_command")).register(tool("weather")).freeze();
    AgentRequestAssembler assembler =
        new AgentRequestAssembler(
            config(), registry, new AgentSystemPrompt(AgentParticipationConfig.from(null)));
    AgentContext context =
        new AgentContext("programming", "alice", "trip", "hash", false, List.of("alice"));
    AgentInvocation invocation =
        new AgentInvocation(context, "tell me about jill user", AgentInvocationMode.DIRECT);

    AgentPreparedRequest request =
        assembler.assemble(
            invocation,
            List.of(LlmMessage.user("old question"), LlmMessage.assistant("old answer", List.of())),
            "recent room context");

    assertEquals(
        Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY), request.requiredFreshTool());
    assertEquals(Optional.of("jill"), request.requiredFreshNick());
    assertEquals("user", request.messages().getLast().role());
    assertTrue(request.contextualizedPrompt().contains("tell me about jill user"));
    assertEquals(2, request.definitions().size());

    AgentPreparedRequest moderation =
        assembler.assemble(
            new AgentInvocation(context, "possible abuse", AgentInvocationMode.MODERATION),
            List.of(),
            "");
    assertTrue(moderation.requiredFreshTool().isEmpty());
    assertFalse(moderation.definitions().isEmpty());
    assertEquals(
        "run_command",
        moderation.definitions().getFirst().getAsJsonObject("function").get("name").getAsString());
  }

  private AgentTool tool(String name) {
    return new AgentTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public AgentToolResult execute(AgentContext context, JsonObject arguments) {
        return AgentToolResult.success(name, arguments);
      }
    };
  }

  private AgentConfig config() {
    return new AgentConfig(
        true,
        URI.create("http://localhost"),
        Optional.empty(),
        "",
        Duration.ofSeconds(1),
        1,
        4,
        2,
        2,
        100,
        100,
        2,
        Duration.ofHours(1),
        0,
        Duration.ZERO);
  }
}
