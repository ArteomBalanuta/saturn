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
    assertEquals(1, moderation.definitions().size());
  }

  @Test
  void doesNotOverflowContextBudgetForLargePromptLimits() {
    AgentRequestAssembler assembler =
        new AgentRequestAssembler(
            configWithPromptChars(Integer.MAX_VALUE),
            new AgentToolRegistry().register(tool("run_command")).freeze(),
            new AgentSystemPrompt(AgentParticipationConfig.from(null)));
    AgentContext context = new AgentContext("room", "alice", null, null, false, List.of("alice"));

    AgentPreparedRequest request =
        assembler.assemble(
            new AgentInvocation(context, "current", AgentInvocationMode.DIRECT),
            List.of(LlmMessage.user("history".repeat(40_000))),
            "recent");

    assertEquals(3, request.messages().size());
    assertEquals("history".repeat(40_000), request.messages().get(1).content());
  }

  @Test
  void preservesSystemHistoryUserOrderingAndDropsOldestHistoryOverBudget() {
    AgentToolRegistry registry = new AgentToolRegistry().register(tool("run_command")).freeze();
    AgentRequestAssembler assembler =
        new AgentRequestAssembler(
            config(), registry, new AgentSystemPrompt(AgentParticipationConfig.from(null)));
    AgentContext context = new AgentContext("room", "alice", null, null, false, List.of("alice"));
    AgentPreparedRequest request =
        assembler.assemble(
            new AgentInvocation(context, "current", AgentInvocationMode.DIRECT),
            List.of(
                LlmMessage.user("old"),
                LlmMessage.assistant("middle", List.of()),
                LlmMessage.user("latest")),
            "recent");

    assertEquals("system", request.messages().getFirst().role());
    assertEquals("old", request.messages().get(1).content());
    assertEquals("middle", request.messages().get(2).content());
    assertEquals("latest", request.messages().get(3).content());
    assertEquals("user", request.messages().getLast().role());

    AgentPreparedRequest bounded =
        assembler.assemble(
            new AgentInvocation(context, "current", AgentInvocationMode.DIRECT),
            List.of(
                LlmMessage.user("first".repeat(20_000)),
                LlmMessage.assistant("second".repeat(20_000), List.of()),
                LlmMessage.user("third".repeat(20_000))),
            "recent");

    assertEquals(2, bounded.messages().size());
    assertEquals("system", bounded.messages().getFirst().role());
    assertEquals("user", bounded.messages().getLast().role());
    assertTrue(bounded.messages().getLast().content().contains("current"));
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
    return configWithPromptChars(100);
  }

  private AgentConfig configWithPromptChars(int maxPromptChars) {
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
        maxPromptChars,
        100,
        2,
        Duration.ofHours(1),
        0,
        Duration.ZERO);
  }
}
