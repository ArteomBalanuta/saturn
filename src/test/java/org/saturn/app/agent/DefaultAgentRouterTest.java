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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.saturn.app.agent.tool.RunCommandTool;

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
    assertTrue(memory.appended.getFirst().contains("@alice"));
    assertTrue(memory.appended.getFirst().contains("who is here?"));
    assertEquals("There are users in the room.", memory.appended.getLast());
  }

  @Test
  void labelsPersistedSharedHistoryAndDirectsTheModelToUseItForFollowUps() throws Exception {
    RecordingMemory memory =
        new RecordingMemory(
            List.of(
                org.saturn.app.agent.llm.LlmMessage.user(
                    "Public Saturn message from @alice in #programming:\n"
                        + "How many users are in lounge?"),
                org.saturn.app.agent.llm.LlmMessage.assistant(
                    "There are 17 users in lounge.", List.of())));
    ScriptedClient client =
        new ScriptedClient(new LlmResponse("You asked about lounge.", List.of(), "stop"));
    DefaultAgentRouter router =
        new DefaultAgentRouter(config(2, 1_000), client, new AgentToolRegistry().freeze(), memory);
    AgentContext bob =
        new AgentContext("programming", "bob", "trip-b", "hash-b", false, List.of("alice", "bob"));

    router.route(new AgentInvocation(bob, "Which room did Alice ask about?"));

    LlmRequest request = client.requests.getFirst();
    assertEquals(
        List.of("system", "user", "assistant", "user"),
        request.messages().stream().map(org.saturn.app.agent.llm.LlmMessage::role).toList());
    assertTrue(request.messages().getFirst().content().contains("CONVERSATION CONTINUITY"));
    assertTrue(request.messages().getFirst().content().contains("same conversation, not a new session"));
    assertTrue(request.messages().getLast().content().contains("@bob"));
    assertTrue(request.messages().getLast().content().contains("Which room did Alice ask about?"));
  }

  @Test
  void serializesRequestsThatBelongToTheSameSharedRoomSession() throws Exception {
    CountDownLatch firstEntered = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    CountDownLatch secondEntered = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    List<LlmRequest> requests = new CopyOnWriteArrayList<>();
    LlmClient client =
        request -> {
          requests.add(request);
          int call = calls.incrementAndGet();
          if (call == 1) {
            firstEntered.countDown();
            try {
              if (!releaseFirst.await(2, TimeUnit.SECONDS)) {
                throw new LlmException("test timed out waiting to release first request");
              }
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
              throw new LlmException("test interrupted", exception);
            }
            return new LlmResponse("first answer", List.of(), "stop");
          }
          secondEntered.countDown();
          return new LlmResponse("second answer", List.of(), "stop");
        };
    SharedRecordingMemory memory = new SharedRecordingMemory();
    DefaultAgentRouter router =
        new DefaultAgentRouter(config(2, 1_000), client, new AgentToolRegistry().freeze(), memory);
    AgentContext alice = context();
    AgentContext bob =
        new AgentContext("programming", "bob", "trip-b", "hash-b", false, List.of("alice", "bob"));

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> router.route(new AgentInvocation(alice, "first question")));
      assertTrue(firstEntered.await(1, TimeUnit.SECONDS));
      var second = executor.submit(() -> router.route(new AgentInvocation(bob, "follow up")));

      boolean secondStartedBeforeFirstCompleted = secondEntered.await(150, TimeUnit.MILLISECONDS);
      releaseFirst.countDown();
      first.get(2, TimeUnit.SECONDS);
      second.get(2, TimeUnit.SECONDS);

      assertFalse(secondStartedBeforeFirstCompleted);
      assertTrue(
          requests.get(1).messages().stream()
              .anyMatch(message -> message.content().contains("first question")));
    } finally {
      releaseFirst.countDown();
    }
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
  void failsRatherThanAnsweringStatelesslyWhenMemoryCannotBeRead() {
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
    ScriptedClient client =
        new ScriptedClient(new LlmResponse("Answer without history.", List.of(), "stop"));
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(2, 100), client, new AgentToolRegistry().freeze(), unavailableMemory);

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () -> router.route(new AgentInvocation(context(), "question")));

    assertTrue(exception.getMessage().contains("memory"));
    assertTrue(client.requests.isEmpty());
  }

  @Test
  void failsRatherThanReturningAnUnpersistedResponse() {
    AgentMemoryStore unavailableMemory =
        new AgentMemoryStore() {
          @Override
          public List<org.saturn.app.agent.llm.LlmMessage> load(
              AgentContext context, AgentConfig config) {
            return List.of();
          }

          @Override
          public void append(
              AgentContext context,
              String userContent,
              String assistantContent,
              AgentConfig config) {
            throw new AgentPersistenceException("database unavailable", null);
          }
        };
    ScriptedClient client =
        new ScriptedClient(new LlmResponse("Unpersisted answer.", List.of(), "stop"));
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(2, 100), client, new AgentToolRegistry().freeze(), unavailableMemory);

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () -> router.route(new AgentInvocation(context(), "question")));

    assertTrue(exception.getMessage().contains("memory"));
    assertEquals(1, client.requests.size());
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

  @Test
  void hydratesMentionContextAndKeepsItsReply() throws Exception {
    ScriptedClient client =
        new ScriptedClient(new LlmResponse("Sun discussed Java yesterday.", List.of(), "stop"));
    RecordingMemory memory = new RecordingMemory();
    List<AgentContext> loadedContexts = new ArrayList<>();
    AgentConversationContextProvider contextProvider =
        context -> {
          loadedContexts.add(context);
          return "{\"rows\":[{\"name\":\"sun\",\"message\":\"Java\"}]}";
        };
    AgentParticipationConfig participationConfig = AgentParticipationConfig.from(null);
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(2, 2_000),
            client,
            new AgentToolRegistry().freeze(),
            memory,
            participationConfig,
            contextProvider);

    AgentResult result =
        router.route(
            new AgentInvocation(
                "mention-1", context(), "who is sun?", AgentInvocationMode.MENTION));

    assertTrue(result.shouldReply());
    assertEquals(List.of(context()), loadedContexts);
    assertTrue(
        client
            .requests
            .getFirst()
            .messages()
            .getFirst()
            .content()
            .contains("\"message\":\"Java\""));
    assertEquals(2, memory.appended.size());
  }

  @Test
  void turnsAmbientNoReplyMarkerIntoAnUnpersistedSilentResult() throws Exception {
    AgentParticipationConfig participationConfig = AgentParticipationConfig.from(null);
    ScriptedClient client =
        new ScriptedClient(new LlmResponse(participationConfig.noReplyMarker(), List.of(), "stop"));
    RecordingMemory memory = new RecordingMemory();
    AtomicInteger contextLoads = new AtomicInteger();
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(2, 2_000),
            client,
            new AgentToolRegistry().freeze(),
            memory,
            participationConfig,
            context -> {
              contextLoads.incrementAndGet();
              return "{\"rows\":[]}";
            });

    AgentResult result =
        router.route(
            new AgentInvocation(
                "ambient-1", context(), "ordinary room comment", AgentInvocationMode.AMBIENT));

    assertFalse(result.shouldReply());
    assertEquals("", result.content());
    assertEquals(1, contextLoads.get());
    assertTrue(memory.appended.isEmpty());
  }

  @Test
  void hydratesPublicDirectInvocationsWithRecentRoomContext() throws Exception {
    List<AgentContext> loadedContexts = new ArrayList<>();
    AgentConversationContextProvider contextProvider =
        context -> {
          loadedContexts.add(context);
          return "{\"rows\":[{\"name\":\"Meth\",\"message\":\"*help\"}]}";
        };
    ScriptedClient client =
        new ScriptedClient(new LlmResponse("Meth used help recently.", List.of(), "stop"));
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(2, 2_000),
            client,
            new AgentToolRegistry().freeze(),
            AgentMemoryStore.none(),
            AgentParticipationConfig.from(null),
            contextProvider);

    AgentResult result = router.route(new AgentInvocation(context(), "what did Meth just do?"));

    assertEquals("Meth used help recently.", result.content());
    assertEquals(List.of(context()), loadedContexts);
    assertTrue(
        client
            .requests
            .getFirst()
            .messages()
            .getFirst()
            .content()
            .contains("\"message\":\"*help\""));
  }

  @Test
  void keepsPrivateDirectInvocationsOutOfPublicRoomContext() throws Exception {
    AtomicInteger contextLoads = new AtomicInteger();
    AgentConversationContextProvider contextProvider =
        context -> {
          contextLoads.incrementAndGet();
          return "{\"rows\":[{\"name\":\"Meth\",\"message\":\"public message\"}]}";
        };
    ScriptedClient client =
        new ScriptedClient(new LlmResponse("private answer", List.of(), "stop"));
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(2, 2_000),
            client,
            new AgentToolRegistry().freeze(),
            AgentMemoryStore.none(),
            AgentParticipationConfig.from(null),
            contextProvider);
    AgentContext whisperContext =
        new AgentContext("programming", "alice", "trip-a", "hash-a", true, List.of("alice", "bob"));

    AgentResult result = router.route(new AgentInvocation(whisperContext, "answer this privately"));

    assertEquals("private answer", result.content());
    assertEquals(0, contextLoads.get());
    assertFalse(
        client.requests.getFirst().messages().getFirst().content().contains("public message"));
  }

  @Test
  void continuesWhenOptionalRoomContextCannotBeRead() throws Exception {
    AtomicInteger contextLoads = new AtomicInteger();
    AgentConversationContextProvider failingProvider =
        context -> {
          contextLoads.incrementAndGet();
          throw new AgentPersistenceException("database unavailable", null);
        };
    ScriptedClient client =
        new ScriptedClient(new LlmResponse("mention fallback", List.of(), "stop"));
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(2, 2_000),
            client,
            new AgentToolRegistry().freeze(),
            AgentMemoryStore.none(),
            AgentParticipationConfig.from(null),
            failingProvider);

    assertEquals(
        "mention fallback",
        router
            .route(
                new AgentInvocation("mention-2", context(), "mention", AgentInvocationMode.MENTION))
            .content());
    assertEquals(1, contextLoads.get());
  }

  @Test
  void publishesRunCommandSchemaForTheCurrentInvocationCapabilities() throws Exception {
    AgentToolRegistry registry =
        new AgentToolRegistry()
            .register(new RunCommandTool((context, command, arguments) -> true))
            .freeze();
    ScriptedClient regularClient =
        new ScriptedClient(new LlmResponse("regular", List.of(), "stop"));
    ScriptedClient creatorClient =
        new ScriptedClient(new LlmResponse("creator", List.of(), "stop"));
    DefaultAgentRouter regularRouter =
        new DefaultAgentRouter(config(2, 2_000), regularClient, registry, AgentMemoryStore.none());
    DefaultAgentRouter creatorRouter =
        new DefaultAgentRouter(config(2, 2_000), creatorClient, registry, AgentMemoryStore.none());
    AgentContext creator =
        new AgentContext(
            "programming",
            "merc",
            "595754",
            "creator-hash",
            false,
            List.of("merc"),
            Set.of(AgentCapability.MODERATION_COMMANDS, AgentCapability.PERMANENT_BAN));

    regularRouter.route(new AgentInvocation(context(), "help"));
    creatorRouter.route(new AgentInvocation(creator, "moderate"));

    assertFalse(commandEnum(regularClient.requests.getFirst()).contains("kick"));
    assertTrue(commandEnum(creatorClient.requests.getFirst()).contains("kick"));
    assertTrue(commandEnum(creatorClient.requests.getFirst()).contains("ban"));
  }

  @Test
  void convertsWrappedCommandIntentIntoARealToolCallWithoutPublishingTheWrapper() throws Exception {
    List<String> executions = new ArrayList<>();
    RunCommandTool commandTool =
        new RunCommandTool(
            (context, command, arguments) -> {
              executions.add(command + " " + arguments);
              return true;
            });
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse("As commanded:\n``weather charlotte``", List.of(), "stop"),
            new LlmResponse(
                "",
                List.of(
                    new LlmToolCall(
                        "weather-1",
                        "run_command",
                        "{\"command\":\"weather\",\"arguments\":\"charlotte\"}")),
                "tool_calls"),
            new LlmResponse("The live weather was sent to the room.", List.of(), "stop"));
    RecordingMemory memory = new RecordingMemory();
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(4, 2_000),
            client,
            new AgentToolRegistry().register(commandTool).freeze(),
            memory);

    AgentResult result = router.route(new AgentInvocation(context(), "please fetch it"));

    assertEquals(List.of("weather charlotte"), executions);
    assertEquals("The live weather was sent to the room.", result.content());
    assertFalse(
        memory.appended.stream().anyMatch(value -> value.contains("``weather charlotte``")));
    assertTrue(client.requests.get(1).messages().getLast().content().contains("run_command"));
    assertTrue(
        client.requests.get(1).messages().getLast().content().contains("exactly one tool call"));
  }

  @Test
  void rewritesWrappedCommandReferenceWithoutExecutingIt() throws Exception {
    List<String> executions = new ArrayList<>();
    RunCommandTool commandTool =
        new RunCommandTool(
            (context, command, arguments) -> {
              executions.add(command + " " + arguments);
              return true;
            });
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse(
                "Meth used `*help`; I will wait for repeated use before acting.",
                List.of(),
                "stop"),
            new LlmResponse(
                "",
                List.of(
                    new LlmToolCall(
                        "response-1",
                        "respond_without_command",
                        "{\"response\":\"Meth used help once. No moderation command was executed.\"}")),
                "tool_calls"));
    RecordingMemory memory = new RecordingMemory();
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(4, 2_000),
            client,
            new AgentToolRegistry().register(commandTool).freeze(),
            memory);

    AgentResult result =
        router.route(
            new AgentInvocation(context(), "if Meth uses *help a few more times, mute him"));

    assertEquals("Meth used help once. No moderation command was executed.", result.content());
    assertTrue(executions.isEmpty());
    assertFalse(memory.appended.stream().anyMatch(value -> value.contains("`*help`")));
    assertEquals(
        Set.of("respond_without_command", "run_command"), toolNames(client.requests.get(1)));
  }

  @Test
  void rejectsNonCommandCorrectionWithUndeclaredArguments() {
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse("Meth used `*help` once.", List.of(), "stop"),
            new LlmResponse(
                "",
                List.of(
                    new LlmToolCall(
                        "response-1",
                        "respond_without_command",
                        "{\"response\":\"Meth used help once.\",\"execute\":true}")),
                "tool_calls"));
    RecordingMemory memory = new RecordingMemory();
    DefaultAgentRouter router = routerWithRunCommand(client, memory);

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () -> router.route(new AgentInvocation(context(), "what did Meth do?")));

    assertTrue(exception.getMessage().contains("invalid non-command correction"));
    assertTrue(memory.appended.isEmpty());
  }

  @Test
  void rejectsCorrectionThatDoesNotReturnTheMatchingToolCall() {
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse("`weather charlotte`", List.of(), "stop"),
            new LlmResponse("I will not call it.", List.of(), "stop"));
    RecordingMemory memory = new RecordingMemory();
    DefaultAgentRouter router = routerWithRunCommand(client, memory);

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () -> router.route(new AgentInvocation(context(), "fetch weather")));

    assertTrue(exception.getMessage().contains("required Saturn tool call"));
    assertTrue(memory.appended.isEmpty());
    assertEquals(2, client.requests.size());
  }

  @Test
  void rejectsCorrectionThatPiggybacksAnotherToolCall() {
    List<String> executions = new ArrayList<>();
    RunCommandTool commandTool =
        new RunCommandTool(
            (context, command, arguments) -> {
              executions.add(command + " " + arguments);
              return true;
            });
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse("`weather charlotte`", List.of(), "stop"),
            new LlmResponse(
                "",
                List.of(
                    new LlmToolCall(
                        "weather-1",
                        "run_command",
                        "{\"command\":\"weather\",\"arguments\":\"charlotte\"}"),
                    new LlmToolCall(
                        "help-1", "run_command", "{\"command\":\"help\",\"arguments\":\"\"}")),
                "tool_calls"),
            new LlmResponse("Both commands completed.", List.of(), "stop"));
    RecordingMemory memory = new RecordingMemory();
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(4, 2_000),
            client,
            new AgentToolRegistry().register(commandTool).freeze(),
            memory);

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () -> router.route(new AgentInvocation(context(), "fetch weather")));

    assertTrue(exception.getMessage().contains("required Saturn tool call"));
    assertTrue(executions.isEmpty());
    assertTrue(memory.appended.isEmpty());
  }

  @Test
  void rewritesWrappedCommandAfterExecutionWithoutRunningItTwice() throws Exception {
    List<String> executions = new ArrayList<>();
    RunCommandTool commandTool =
        new RunCommandTool(
            (context, command, arguments) -> {
              executions.add(command + " " + arguments);
              return true;
            });
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse(
                "",
                List.of(
                    new LlmToolCall(
                        "weather-1",
                        "run_command",
                        "{\"command\":\"weather\",\"arguments\":\"charlotte\"}")),
                "tool_calls"),
            new LlmResponse("Done: `weather charlotte`", List.of(), "stop"),
            new LlmResponse("The live weather was sent to the room.", List.of(), "stop"));
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(4, 2_000),
            client,
            new AgentToolRegistry().register(commandTool).freeze(),
            AgentMemoryStore.none());

    AgentResult result = router.route(new AgentInvocation(context(), "fetch weather"));

    assertEquals(List.of("weather charlotte"), executions);
    assertEquals("The live weather was sent to the room.", result.content());
    assertTrue(client.requests.getLast().tools().isEmpty());
  }

  @Test
  void executesDifferentWrappedCommandAfterAnEarlierCommandSucceeded() throws Exception {
    List<String> executions = new ArrayList<>();
    RunCommandTool commandTool =
        new RunCommandTool(
            (context, command, arguments) -> {
              executions.add(command + " " + arguments);
              return true;
            });
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse(
                "",
                List.of(
                    new LlmToolCall(
                        "weather-1",
                        "run_command",
                        "{\"command\":\"weather\",\"arguments\":\"charlotte\"}")),
                "tool_calls"),
            new LlmResponse("Next: `help`", List.of(), "stop"),
            new LlmResponse(
                "",
                List.of(
                    new LlmToolCall(
                        "help-1", "run_command", "{\"command\":\"help\",\"arguments\":\"\"}")),
                "tool_calls"),
            new LlmResponse("Help was sent to the room.", List.of(), "stop"));
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(4, 2_000),
            client,
            new AgentToolRegistry().register(commandTool).freeze(),
            AgentMemoryStore.none());

    AgentResult result = router.route(new AgentInvocation(context(), "fetch weather, then help"));

    assertEquals(List.of("weather charlotte", "help "), executions);
    assertEquals("Help was sent to the room.", result.content());
    assertTrue(client.requests.get(2).messages().getLast().content().contains("run_command"));
  }

  @Test
  void rewritesWrappedCommandAfterFailedExecutionWithoutClaimingItRan() throws Exception {
    List<String> attempts = new ArrayList<>();
    RunCommandTool commandTool =
        new RunCommandTool(
            (context, command, arguments) -> {
              attempts.add(command + " " + arguments);
              return false;
            });
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse(
                "",
                List.of(
                    new LlmToolCall(
                        "weather-1",
                        "run_command",
                        "{\"command\":\"weather\",\"arguments\":\"charlotte\"}")),
                "tool_calls"),
            new LlmResponse("Try this instead: `weather charlotte`", List.of(), "stop"),
            new LlmResponse("The weather command did not run.", List.of(), "stop"));
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(4, 2_000),
            client,
            new AgentToolRegistry().register(commandTool).freeze(),
            AgentMemoryStore.none());

    AgentResult result = router.route(new AgentInvocation(context(), "fetch weather"));

    assertEquals(List.of("weather charlotte"), attempts);
    assertEquals("The weather command did not run.", result.content());
    assertTrue(client.requests.getLast().tools().isEmpty());
    assertTrue(
        client.requests.getLast().messages().getLast().content().contains("did not execute"));
    assertFalse(
        client.requests.getLast().messages().getLast().content().contains("already executed"));
  }

  @Test
  void preservesUnrelatedInlineCodeInOrdinaryAnswers() throws Exception {
    ScriptedClient client =
        new ScriptedClient(new LlmResponse("Use `List.of()`.", List.of(), "stop"));

    AgentResult result =
        routerWithRunCommand(client, new RecordingMemory())
            .route(new AgentInvocation(context(), "show Java"));

    assertEquals("Use `List.of()`.", result.content());
    assertEquals(1, client.requests.size());
  }

  @Test
  void doesNotAcceptNarratedCommandExecutionWithoutARealToolCall() throws Exception {
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse(
                "I will execute ping now.\n[executes ping command]", List.of(), "stop"),
            new LlmResponse(
                "Checking the user's history now.",
                List.of(
                    new LlmToolCall(
                        "ping-1", "run_command", "{\"command\":\"ping\",\"arguments\":\"\"}")),
                "tool_calls"),
            new LlmResponse("The response time is 184 milliseconds.", List.of(), "stop"));
    RecordingMemory memory = new RecordingMemory();

    AgentResult result =
        routerWithRunCommand(client, memory)
            .route(new AgentInvocation(context(), "run ping"));

    assertEquals("The response time is 184 milliseconds.", result.content());
    assertEquals(3, client.requests.size());
    assertTrue(
        client.requests.get(1).messages().getLast().content().contains("narrated an action"));
    assertFalse(memory.appended.stream().anyMatch(value -> value.contains("executes ping")));
  }

  @Test
  void requiresUserHistoryToolWhenCompletionClaimsItFetchedAUsersHistory() throws Exception {
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse(
                "I will fetch nex's recent message history to provide a complete picture.",
                List.of(),
                "stop"),
            new LlmResponse(
                "",
                List.of(
                    new LlmToolCall(
                        "history-1",
                        "user_message_history",
                        "{\"nick\":\"nex\",\"room\":\"programming\"}")),
                "tool_calls"),
            new LlmResponse("Nex has recently been active in programming.", List.of(), "stop"));
    AgentTool historyTool =
        new AgentTool() {
          @Override
          public String name() {
            return "user_message_history";
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), "nex posted recently");
          }
        };
    RecordingMemory memory = new RecordingMemory();
    DefaultAgentRouter router =
        new DefaultAgentRouter(
            config(4, 2_000),
            client,
            new AgentToolRegistry().register(historyTool).freeze(),
            memory);

    AgentResult result = router.route(new AgentInvocation(context(), "who is nex"));

    assertEquals("Nex has recently been active in programming.", result.content());
    assertEquals(3, client.requests.size());
    assertTrue(memory.appended.stream().noneMatch(value -> value.contains("I will fetch")));
  }

  @Test
  void retriesWithoutPromptCacheWhenNewPromptGetsPreviousAnswer() throws Exception {
    String previousAnswer = "Welcome back. The room is still here.";
    RecordingMemory memory =
        new RecordingMemory(
            List.of(
                org.saturn.app.agent.llm.LlmMessage.user(
                    "Public message from @alice in #programming:\nwb"),
                org.saturn.app.agent.llm.LlmMessage.assistant(previousAnswer, List.of())));
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse(previousAnswer, List.of(), "stop"),
            new LlmResponse("They are discussing the room lock.", List.of(), "stop"));

    AgentResult result =
        routerWithRunCommand(client, memory)
            .route(new AgentInvocation(context(), "what is being discussed?"));

    assertEquals("They are discussing the room lock.", result.content());
    assertEquals(2, client.requests.size());
    assertFalse(client.requests.getFirst().bypassPromptCache());
    assertTrue(client.requests.getLast().bypassPromptCache());
    assertTrue(
        client.requests.getLast().messages().getLast().content().contains("duplicated an earlier"));
    assertEquals("They are discussing the room lock.", memory.appended.getLast());
  }

  @Test
  void rejectsAnswerThatRemainsStaleAfterCacheBypass() {
    String previousAnswer = "Welcome back. The room is still here.";
    RecordingMemory memory =
        new RecordingMemory(
            List.of(
                org.saturn.app.agent.llm.LlmMessage.user(
                    "Public message from @alice in #programming:\nwb"),
                org.saturn.app.agent.llm.LlmMessage.assistant(previousAnswer, List.of())));
    ScriptedClient client =
        new ScriptedClient(
            new LlmResponse(previousAnswer, List.of(), "stop"),
            new LlmResponse(previousAnswer, List.of(), "stop"));

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () ->
                routerWithRunCommand(client, memory)
                    .route(new AgentInvocation(context(), "what is being discussed?")));

    assertTrue(exception.getMessage().contains("stale response"));
    assertTrue(memory.appended.isEmpty());
  }

  @Test
  void permitsSameAnswerWhenTheUserRepeatedTheSamePrompt() throws Exception {
    String repeatedPrompt = "Public Saturn message from @alice in #programming:\nstatus";
    String repeatedAnswer = "Everything is operational.";
    RecordingMemory memory =
        new RecordingMemory(
            List.of(
                org.saturn.app.agent.llm.LlmMessage.user(repeatedPrompt),
                org.saturn.app.agent.llm.LlmMessage.assistant(repeatedAnswer, List.of())));
    ScriptedClient client =
        new ScriptedClient(new LlmResponse(repeatedAnswer, List.of(), "stop"));

    AgentResult result =
        routerWithRunCommand(client, memory)
            .route(new AgentInvocation(context(), "status"));

    assertEquals(repeatedAnswer, result.content());
    assertEquals(1, client.requests.size());
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

  private Set<String> commandEnum(LlmRequest request) {
    return request.tools().stream()
        .map(tool -> tool.getAsJsonObject("function"))
        .filter(function -> function.get("name").getAsString().equals("run_command"))
        .findFirst()
        .orElseThrow()
        .getAsJsonObject("parameters")
        .getAsJsonObject("properties")
        .getAsJsonObject("command")
        .getAsJsonArray("enum")
        .asList()
        .stream()
        .map(value -> value.getAsString())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private DefaultAgentRouter routerWithRunCommand(ScriptedClient client, AgentMemoryStore memory) {
    return new DefaultAgentRouter(
        config(4, 2_000),
        client,
        new AgentToolRegistry()
            .register(new RunCommandTool((context, command, arguments) -> true))
            .freeze(),
        memory);
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
    private final List<org.saturn.app.agent.llm.LlmMessage> loaded;

    private RecordingMemory() {
      this(List.of());
    }

    private RecordingMemory(List<org.saturn.app.agent.llm.LlmMessage> loaded) {
      this.loaded = List.copyOf(loaded);
    }

    @Override
    public List<org.saturn.app.agent.llm.LlmMessage> load(
        AgentContext context, AgentConfig config) {
      return loaded;
    }

    @Override
    public void append(
        AgentContext context, String userContent, String assistantContent, AgentConfig config) {
      appended.add(userContent);
      appended.add(assistantContent);
    }
  }

  private static final class SharedRecordingMemory implements AgentMemoryStore {
    private final List<org.saturn.app.agent.llm.LlmMessage> messages = new ArrayList<>();

    @Override
    public synchronized List<org.saturn.app.agent.llm.LlmMessage> load(
        AgentContext context, AgentConfig config) {
      return List.copyOf(messages);
    }

    @Override
    public synchronized void append(
        AgentContext context, String userContent, String assistantContent, AgentConfig config) {
      messages.add(org.saturn.app.agent.llm.LlmMessage.user(userContent));
      messages.add(org.saturn.app.agent.llm.LlmMessage.assistant(assistantContent, List.of()));
    }
  }
}
