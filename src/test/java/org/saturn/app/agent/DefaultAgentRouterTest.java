package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.llm.LlmToolCall;
import org.saturn.app.agent.persistence.AgentDatabaseSchema;
import org.saturn.app.agent.persistence.AgentPersistenceException;
import org.saturn.app.agent.persistence.AgentSqlResult;
import org.saturn.app.agent.sql.ValidatedAgentSql;
import org.saturn.app.agent.tool.DatabaseSchemaTool;
import org.saturn.app.agent.tool.DatabaseSqlTool;

class DefaultAgentRouterTest {
  @Test
  void routesToolResultsBackToModelAndPersistsCompletedTurn() throws Exception {
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse(
                "",
                List.of(new LlmToolCall("call-1", "echo", "{\"value\":\"room\"}")),
                "tool_calls"),
            new LlmResponse("There are users in the room.", List.of(), "stop"));
    RecordingMemory memory = new RecordingMemory();
    AgentTool tool =
        new AgentTool() {
          @Override
          public String name() {
            return "echo";
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), arguments.get("value").getAsString());
          }
        };
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(4, 100), client, new AgentToolRegistry().register(tool).freeze(), memory);

    AgentInvocation invocation = new AgentInvocation(context(), "who is here?");
    AgentResult result = router.route(invocation);

    assertEquals("There are users in the room.", result.content());
    assertEquals(invocation.requestId(), result.correlationId());
    assertEquals(2, client.requests.size());
    assertTrue(
        client.requests.getFirst().messages().getFirst().content().contains("user-authored text"));
    assertEquals("tool", client.requests.get(1).messages().getLast().role());
    assertEquals(List.of("who is here?", "There are users in the room."), memory.appended);
  }

  @Test
  void forcesFinalNoToolsResponseWhenCallBudgetIsExhausted() throws Exception {
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse("", List.of(new LlmToolCall("1", "echo", "{}")), "tool_calls"),
            new LlmResponse("", List.of(new LlmToolCall("2", "echo", "{\"n\":2}")), "tool_calls"),
            new LlmResponse("Partial work summarized.", List.of(), "stop"));
    AgentTool tool =
        new AgentTool() {
          public String name() {
            return "echo";
          }

          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), "ok");
          }
        };
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(1, 100),
            client,
            new AgentToolRegistry().register(tool).freeze(),
            AgentMemoryStore.none());

    AgentResult result = router.route(new AgentInvocation(context(), "do work"));

    assertEquals("Partial work summarized.", result.content());
    assertEquals(3, client.requests.size());
    assertTrue(client.requests.getLast().tools().isEmpty());
  }

  @Test
  void rejectsOversizedPromptAndTruncatesOutput() throws Exception {
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(2, 5),
            new ScriptedClient(new LlmResponse("123456789", List.of(), "stop")),
            new AgentToolRegistry().freeze(),
            AgentMemoryStore.none());

    assertThrows(
        AgentRoutingException.class, () -> router.route(new AgentInvocation(context(), "123456")));
    AgentResult result = router.route(new AgentInvocation(context(), "12345"));

    assertEquals("12345", result.content());
    assertFalse(result.correlationId().isBlank());
  }

  @Test
  void continuesWithoutHistoryWhenMemoryCannotBeRead() throws Exception {
    AgentMemoryStore unavailableMemory =
        new AgentMemoryStore() {
          @Override
          public List<org.saturn.app.agent.llm.LlmMessage> load(
              AgentContext context, AgentConfig config) {
            throw new AgentPersistenceException("database unavailable", null);
          }

          @Override
          public void append(
              AgentContext context,
              String userContent,
              String assistantContent,
              AgentConfig config) {}
        };
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(2, 100),
            new ScriptedClient(new LlmResponse("Answer without history.", List.of(), "stop")),
            new AgentToolRegistry().freeze(),
            unavailableMemory);

    AgentResult result = router.route(new AgentInvocation(context(), "question"));

    assertEquals("Answer without history.", result.content());
  }

  @Test
  void preservesProviderFailureReasonForOperationalLogs() {
    LlmClient unavailable =
        request -> {
          throw new LlmException("LLM endpoint timed out after 30000 ms");
        };
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(2, 100), unavailable, new AgentToolRegistry().freeze(), AgentMemoryStore.none());

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () -> router.route(new AgentInvocation(context(), "question")));

    assertEquals(
        "Agent provider failed: LLM endpoint timed out after 30000 ms", exception.getMessage());
  }

  @Test
  void appliesPromptAndOutputLimitsByUnicodeCodePoint() throws Exception {
    String content = "1234😀";
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(2, 5),
            new ScriptedClient(new LlmResponse(content, List.of(), "stop")),
            new AgentToolRegistry().freeze(),
            AgentMemoryStore.none());

    AgentResult result = router.route(new AgentInvocation(context(), content));

    assertEquals(content, result.content());
  }

  @Test
  void routesAdminSchemaThenDynamicSqlWhileHidingBothToolsFromRegularCallers() throws Exception {
    AgentDatabaseSchema schema =
        new AgentDatabaseSchema(
            List.of(new AgentDatabaseSchema.Table("messages", List.of(), List.of(), List.of())));
    AgentSqlConfig sqlConfig =
        new AgentSqlConfig(true, 4_000, 50, 32, 2_000, 32_000, Duration.ofSeconds(1));
    AgentToolRegistry registry =
        new AgentToolRegistry()
            .register(new DatabaseSchemaTool(() -> schema, sqlConfig))
            .register(
                new DatabaseSqlTool(
                    () -> schema,
                    (sql, ignoredSchema) -> new ValidatedAgentSql(sql, "fingerprint"),
                    (sql, ignoredConfig) ->
                        new AgentSqlResult(
                            List.of("count"), List.of(List.of((Object) 12L)), false, 1),
                    sqlConfig))
            .freeze();
    ScriptedClient adminClient =
        new ScriptedClient(
            new LlmResponse(
                "", List.of(new LlmToolCall("schema", "database_schema", "{}")), "tool_calls"),
            new LlmResponse(
                "",
                List.of(
                    new LlmToolCall(
                        "sql", "database_sql", "{\"sql\":\"SELECT count(*) FROM messages\"}")),
                "tool_calls"),
            new LlmResponse("There are 12 stored messages.", List.of(), "stop"));
    DefaultAgentRouter adminRouter =
        new DefaultAgentRouter(config(4, 1_000), adminClient, registry, AgentMemoryStore.none());

    AgentResult result =
        adminRouter.route(new AgentInvocation(adminContext(), "How many messages exist?"));

    assertEquals("There are 12 stored messages.", result.content());
    assertEquals(
        Set.of("database_schema", "database_sql"), toolNames(adminClient.requests.getFirst()));
    assertTrue(
        adminClient.requests.getFirst().messages().getFirst().content().contains("purpose-built"));
    assertTrue(adminClient.requests.getLast().messages().getLast().content().contains("12"));

    ScriptedClient regularClient =
        new ScriptedClient(new LlmResponse("I cannot inspect that data.", List.of(), "stop"));
    DefaultAgentRouter regularRouter =
        new DefaultAgentRouter(config(4, 1_000), regularClient, registry, AgentMemoryStore.none());
    regularRouter.route(new AgentInvocation(context(), "How many messages exist?"));

    assertTrue(regularClient.requests.getFirst().tools().isEmpty());
  }

  private AgentContext context() {
    return new AgentContext(
        "programming", "alice", "trip-a", "hash-a", false, List.of("alice", "bob"));
  }

  private AgentContext adminContext() {
    AgentContext context = context();
    return new AgentContext(
        context.room(),
        context.nick(),
        context.trip(),
        context.hash(),
        context.whisper(),
        context.roomUsers(),
        Set.of(AgentCapability.DYNAMIC_SQL));
  }

  private Set<String> toolNames(LlmRequest request) {
    return request.tools().stream()
        .map(tool -> tool.getAsJsonObject("function").get("name").getAsString())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private AgentConfig config(int maxToolCalls, int maxChars) {
    return new AgentConfig(
        true,
        URI.create("http://localhost"),
        Optional.empty(),
        "",
        Duration.ofSeconds(1),
        1,
        maxToolCalls,
        3,
        2,
        maxChars,
        maxChars,
        2,
        Duration.ofHours(1),
        0,
        Duration.ZERO);
  }

  private static final class ScriptedClient implements LlmClient {
    private final ArrayDeque<LlmResponse> responses;
    private final List<LlmRequest> requests = new ArrayList<>();

    private ScriptedClient(LlmResponse... responses) {
      this.responses = new ArrayDeque<>(List.of(responses));
    }

    @Override
    public LlmResponse complete(LlmRequest request) throws LlmException {
      requests.add(request);
      if (responses.isEmpty()) {
        throw new LlmException("No scripted response");
      }
      return responses.removeFirst();
    }
  }

  private static final class RecordingMemory implements AgentMemoryStore {
    private final List<String> appended = new ArrayList<>();

    @Override
    public List<org.saturn.app.agent.llm.LlmMessage> load(
        AgentContext context, AgentConfig config) {
      return List.of();
    }

    @Override
    public void append(
        AgentContext context, String userContent, String assistantContent, AgentConfig config) {
      appended.add(userContent);
      appended.add(assistantContent);
    }
  }
}
