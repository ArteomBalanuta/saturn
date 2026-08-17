package org.saturn.app.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.AgentCapability;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentSqlConfig;
import org.saturn.app.agent.AgentToolDescriptor;
import org.saturn.app.agent.AgentToolResult;
import org.saturn.app.agent.persistence.AgentDatabaseSchema;
import org.saturn.app.agent.persistence.AgentPersistenceException;
import org.saturn.app.agent.persistence.AgentQueryRepository;
import org.saturn.app.agent.persistence.AgentSqlResult;
import org.saturn.app.agent.sql.AgentSqlErrorCode;
import org.saturn.app.agent.sql.AgentSqlPolicyException;
import org.saturn.app.agent.sql.ValidatedAgentSql;

class SaturnAgentToolsTest {
  @Test
  void rejectsModerationCommandForSomeoneOtherThanTheTrustedTarget() {
    AtomicInteger executions = new AtomicInteger();
    RunCommandTool tool =
        new RunCommandTool(
            (context, command, arguments) -> {
              executions.incrementAndGet();
              return true;
            });
    AgentContext context =
        new AgentContext(
            "programming",
            "saturn",
            "bot-trip",
            "bot-hash",
            false,
            List.of("alice", "bob"),
            Set.of(AgentCapability.MODERATION_COMMANDS),
            "alice");
    JsonObject arguments = new JsonObject();
    arguments.addProperty("command", "mute");
    arguments.addProperty("arguments", "bob");

    AgentToolResult result = tool.execute(context, arguments);

    assertTrue(result.isError());
    assertEquals(0, executions.get());
  }

  @Test
  void roomAndDatabaseToolsReturnStructuredData() {
    AgentContext context = context();
    RoomUsersTool roomUsers =
        new RoomUsersTool(
            room ->
                Optional.of(new AgentRoomDirectory.RoomSnapshot(room, List.of("alice", "bob"))));
    AgentQueryRepository repository =
        (name, arguments, ignored) -> {
          JsonObject result = new JsonObject();
          result.addProperty("query", name);
          return result;
        };
    DatabaseQueryTool database = new DatabaseQueryTool(repository);
    JsonObject query = new JsonObject();
    query.addProperty("query", "message_count");

    AgentToolResult users = roomUsers.execute(context, new JsonObject());
    AgentToolResult count = database.execute(context, query);

    assertFalse(users.isError());
    assertTrue(users.content().contains("alice"));
    assertEquals(
        "message_count",
        com.google.gson.JsonParser.parseString(count.content())
            .getAsJsonObject()
            .get("query")
            .getAsString());
  }

  @Test
  void roomUsersToolUsesTheExplicitRoomInsteadOfTheCurrentRoom() {
    AgentRoomDirectory directory =
        room ->
            "lounge".equalsIgnoreCase(room)
                ? Optional.of(
                    new AgentRoomDirectory.RoomSnapshot(
                        "lounge", List.of("lounge-user", "another-user")))
                : Optional.empty();
    RoomUsersTool tool = new RoomUsersTool(directory);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("room", "Lounge");

    AgentToolResult result = tool.execute(context(), arguments);

    assertFalse(result.isError());
    JsonObject content = com.google.gson.JsonParser.parseString(result.content()).getAsJsonObject();
    assertEquals("lounge", content.get("room").getAsString());
    assertEquals(2, content.get("count").getAsInt());
    assertTrue(content.getAsJsonArray("users").toString().contains("lounge-user"));
    assertFalse(content.getAsJsonArray("users").toString().contains("alice"));
  }

  @Test
  void roomUsersToolReportsInvalidAndUnmanagedRooms() {
    RoomUsersTool tool = new RoomUsersTool(room -> Optional.empty());
    JsonObject invalid = new JsonObject();
    invalid.addProperty("room", " ");

    AgentToolResult invalidResult = tool.execute(context(), invalid);
    AgentToolResult unmanagedResult = tool.execute(context(), new JsonObject());

    assertTrue(invalidResult.isError());
    assertTrue(invalidResult.content().contains("non-blank room"));
    assertTrue(unmanagedResult.isError());
    assertTrue(unmanagedResult.content().contains("programming"));
  }

  @Test
  void roomUsersToolExposesDescriptorAndClosedRoomSchema() {
    RoomUsersTool tool =
        new RoomUsersTool(
            room -> Optional.of(new AgentRoomDirectory.RoomSnapshot(room, List.of())));

    AgentToolDescriptor descriptor = tool.descriptor(context());

    assertEquals("room_users", descriptor.name());
    assertEquals("room", descriptor.category());
    assertEquals("object", descriptor.parameters().get("type").getAsString());
    assertFalse(descriptor.parameters().get("additionalProperties").getAsBoolean());
    assertEquals(
        "string",
        descriptor
            .parameters()
            .getAsJsonObject("properties")
            .getAsJsonObject("room")
            .get("type")
            .getAsString());
  }

  @Test
  void roomUsersToolRejectsNullDirectory() {
    assertThrows(NullPointerException.class, () -> new RoomUsersTool(null));
  }

  @Test
  void userMessageHistoryToolRequiresNickAndDelegatesBoundedNamedQuery() {
    AtomicReference<String> queryName = new AtomicReference<>();
    AtomicReference<JsonObject> queryArguments = new AtomicReference<>();
    AgentQueryRepository repository =
        (name, arguments, ignored) -> {
          queryName.set(name);
          queryArguments.set(arguments.deepCopy());
          JsonObject result = new JsonObject();
          result.addProperty("matched", true);
          return result;
        };
    UserMessageHistoryTool tool = new UserMessageHistoryTool(repository);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "jetty");
    arguments.addProperty("room", " lounge ");
    arguments.addProperty("limit", 20);

    AgentToolResult missingNick = tool.execute(context(), new JsonObject());
    AgentToolResult result = tool.execute(context(), arguments);

    assertTrue(missingNick.isError());
    assertFalse(result.isError());
    assertEquals("recent_messages_for_user", queryName.get());
    assertEquals("jetty", queryArguments.get().get("nick").getAsString());
    assertEquals("lounge", queryArguments.get().get("room").getAsString());
    assertTrue(result.content().contains("matched"));
  }

  @Test
  void userMessageHistoryToolUnescapesMarkdownEscapedNickBeforeQuerying() {
    AtomicReference<JsonObject> queryArguments = new AtomicReference<>();
    AgentQueryRepository repository =
        (name, arguments, ignored) -> {
          queryArguments.set(arguments.deepCopy());
          return new JsonObject();
        };
    UserMessageHistoryTool tool = new UserMessageHistoryTool(repository);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "@Et\\_In\\_Arcadia\\_Ego");

    AgentToolResult result = tool.execute(context(), arguments);

    assertFalse(result.isError());
    assertEquals("Et_In_Arcadia_Ego", queryArguments.get().get("nick").getAsString());
  }

  @Test
  void userMessageHistoryDefaultsToAllRoomsForFollowUpRequests() {
    AtomicReference<JsonObject> queryArguments = new AtomicReference<>();
    AgentQueryRepository repository =
        (name, arguments, ignored) -> {
          queryArguments.set(arguments.deepCopy());
          return new JsonObject();
        };
    UserMessageHistoryTool tool = new UserMessageHistoryTool(repository);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "sun");

    AgentToolResult result = tool.execute(context(), arguments);

    assertFalse(result.isError());
    assertFalse(queryArguments.get().has("room"));
    assertTrue(tool.description().contains("all rooms"));
  }

  @Test
  void userMessageHistoryReportsReturnedEvidenceRange() {
    AgentQueryRepository repository =
        (name, arguments, ignored) -> {
          JsonArray rows = new JsonArray();
          JsonObject older = new JsonObject();
          older.addProperty("message", "older");
          older.addProperty("createdOn", 100L);
          JsonObject newer = new JsonObject();
          newer.addProperty("message", "newer");
          newer.addProperty("createdOn", 300L);
          rows.add(older);
          rows.add(newer);
          JsonObject result = new JsonObject();
          result.add("rows", rows);
          return result;
        };
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "jill");

    AgentToolResult result = new UserMessageHistoryTool(repository).execute(context(), arguments);

    JsonObject content = JsonParser.parseString(result.content()).getAsJsonObject();
    assertFalse(result.isError());
    assertTrue(content.has("returnedCount"));
    assertTrue(content.has("oldestCreatedOn"));
    assertTrue(content.has("newestCreatedOn"));
    assertEquals(2, content.get("returnedCount").getAsInt());
    assertEquals(100L, content.get("oldestCreatedOn").getAsLong());
    assertEquals(300L, content.get("newestCreatedOn").getAsLong());
    assertEquals(
        "older",
        content.getAsJsonArray("rows").get(0).getAsJsonObject().get("message").getAsString());
    assertEquals(
        "newer",
        content.getAsJsonArray("rows").get(1).getAsJsonObject().get("message").getAsString());
  }

  @Test
  void userMessageHistoryReportsEmptyEvidenceWithoutInventingATimeRange() {
    AgentQueryRepository repository =
        (name, arguments, ignored) -> {
          JsonObject result = new JsonObject();
          result.add("rows", new JsonArray());
          return result;
        };
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "missing");

    AgentToolResult result = new UserMessageHistoryTool(repository).execute(context(), arguments);

    JsonObject content = JsonParser.parseString(result.content()).getAsJsonObject();
    assertTrue(content.has("returnedCount"));
    assertTrue(content.has("oldestCreatedOn"));
    assertTrue(content.has("newestCreatedOn"));
    assertEquals(0, content.get("returnedCount").getAsInt());
    assertTrue(content.get("oldestCreatedOn").isJsonNull());
    assertTrue(content.get("newestCreatedOn").isJsonNull());
  }

  @Test
  void userMessageHistoryUsesTheSameTimestampForSingleRowRange() {
    AgentQueryRepository repository =
        (name, arguments, ignored) -> {
          JsonObject row = new JsonObject();
          row.addProperty("message", "only message");
          row.addProperty("createdOn", 200L);
          JsonArray rows = new JsonArray();
          rows.add(row);
          JsonObject result = new JsonObject();
          result.add("rows", rows);
          return result;
        };
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "solo");

    AgentToolResult result = new UserMessageHistoryTool(repository).execute(context(), arguments);

    JsonObject content = JsonParser.parseString(result.content()).getAsJsonObject();
    assertTrue(content.has("returnedCount"));
    assertTrue(content.has("oldestCreatedOn"));
    assertTrue(content.has("newestCreatedOn"));
    assertEquals(1, content.get("returnedCount").getAsInt());
    assertEquals(200L, content.get("oldestCreatedOn").getAsLong());
    assertEquals(200L, content.get("newestCreatedOn").getAsLong());
  }

  @Test
  void databaseToolExposesBoundedRoomMessageHistoryQuery() {
    AtomicReference<String> queryName = new AtomicReference<>();
    AtomicReference<JsonObject> queryArguments = new AtomicReference<>();
    AgentQueryRepository repository =
        (name, arguments, ignored) -> {
          queryName.set(name);
          queryArguments.set(arguments.deepCopy());
          return new JsonObject();
        };
    DatabaseQueryTool tool = new DatabaseQueryTool(repository);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("query", "recent_messages_for_room");
    arguments.addProperty("room", "lounge");
    arguments.addProperty("limit", 12);

    AgentToolResult result = tool.execute(context(), arguments);

    assertFalse(result.isError());
    assertEquals("recent_messages_for_room", queryName.get());
    assertEquals("lounge", queryArguments.get().get("room").getAsString());
    assertEquals(12, queryArguments.get().get("limit").getAsInt());
    assertTrue(
        tool.parameters()
            .getAsJsonObject("properties")
            .getAsJsonObject("query")
            .getAsJsonArray("enum")
            .contains(new com.google.gson.JsonPrimitive("recent_messages_for_room")));
  }

  @Test
  void databaseToolRejectsMalformedQueryNamesWithoutThrowing() {
    DatabaseQueryTool tool = new DatabaseQueryTool((name, arguments, context) -> new JsonObject());
    JsonObject arguments = new JsonObject();
    arguments.add("query", new JsonObject());

    AgentToolResult result = tool.execute(context(), arguments);

    assertTrue(result.isError());
    assertEquals("Query is not approved", result.content());
  }

  @Test
  void databaseToolRejectsMissingQueryNamesWithoutCallingTheRepository() {
    AtomicInteger calls = new AtomicInteger();
    DatabaseQueryTool tool =
        new DatabaseQueryTool(
            (name, arguments, context) -> {
              calls.incrementAndGet();
              return new JsonObject();
            });

    AgentToolResult result = tool.execute(context(), new JsonObject());

    assertTrue(result.isError());
    assertEquals("Missing required query name", result.content());
    assertEquals(0, calls.get());
  }

  @Test
  void databaseToolTranslatesRepositoryFailuresToStableToolErrors() {
    DatabaseQueryTool rejected =
        new DatabaseQueryTool(
            (name, arguments, context) -> {
              throw new IllegalArgumentException("unknown query");
            });
    DatabaseQueryTool failed =
        new DatabaseQueryTool(
            (name, arguments, context) -> {
              throw new IllegalStateException("database unavailable");
            });
    JsonObject arguments = new JsonObject();
    arguments.addProperty("query", "message_count");

    AgentToolResult rejectedResult = rejected.execute(context(), arguments);
    AgentToolResult failedResult = failed.execute(context(), arguments);

    assertTrue(rejectedResult.isError());
    assertEquals("Query is not approved", rejectedResult.content());
    assertTrue(failedResult.isError());
    assertEquals("Database query failed", failedResult.content());
  }

  @Test
  void databaseToolDescriptorPublishesTheApprovedQueryContract() {
    DatabaseQueryTool tool = new DatabaseQueryTool((name, arguments, context) -> new JsonObject());

    AgentToolDescriptor descriptor = tool.descriptor(context());

    assertEquals("database_query", descriptor.name());
    assertEquals("database", descriptor.category());
    assertFalse(descriptor.parameters().get("additionalProperties").getAsBoolean());
    assertTrue(descriptor.examples().getFirst().arguments().contains("recent_messages_for_room"));
  }

  @Test
  void commandToolPublishesAndEnforcesTheSameCapabilityAwareCatalog() {
    AtomicReference<String> invoked = new AtomicReference<>();
    SaturnCommandGateway gateway =
        (context, command, arguments) -> {
          invoked.set(command + " " + arguments);
          return true;
        };
    RunCommandTool tool = new RunCommandTool(gateway);

    JsonObject safe = new JsonObject();
    safe.addProperty("command", "weather");
    safe.addProperty("arguments", "Chisinau");
    JsonObject recursive = new JsonObject();
    recursive.addProperty("command", "l");
    JsonObject privileged = new JsonObject();
    privileged.addProperty("command", "sql");
    JsonObject kick = new JsonObject();
    kick.addProperty("command", "kick");
    kick.addProperty("arguments", "spammer");
    JsonObject ban = new JsonObject();
    ban.addProperty("command", "ban");
    ban.addProperty("arguments", "spammer");

    Set<String> regularCatalog = commandEnum(tool, context());
    Set<String> moderatorCatalog = commandEnum(tool, moderatorContext());
    Set<String> creatorCatalog = commandEnum(tool, creatorContext());

    AgentToolResult safeResult = tool.execute(context(), safe);

    assertFalse(safeResult.isError());
    assertEquals(
        "Saturn command 'weather' executed; its output was sent to the room. No other Saturn command was executed.",
        safeResult.content());
    assertEquals("weather Chisinau", invoked.get());
    assertTrue(tool.execute(context(), recursive).isError());
    assertTrue(tool.execute(context(), privileged).isError());
    assertFalse(regularCatalog.contains("kick"));
    assertFalse(regularCatalog.contains("ban"));
    assertTrue(moderatorCatalog.containsAll(Set.of("captcha", "mute", "kick", "shadowban")));
    assertFalse(moderatorCatalog.contains("ban"));
    assertTrue(creatorCatalog.contains("ban"));
    assertTrue(tool.execute(context(), kick).isError());
    assertFalse(tool.execute(moderatorContext(), kick).isError());
    assertTrue(tool.execute(moderatorContext(), ban).isError());
    assertFalse(tool.execute(creatorContext(), ban).isError());
  }

  @Test
  void commandToolDescriptorExplainsAuthorityAndDelivery() {
    RunCommandTool tool = new RunCommandTool((context, command, arguments) -> true);

    assertEquals("commands", tool.descriptor(context()).category());
    assertEquals(
        org.saturn.app.agent.ToolAccess.AUTHORIZED_CALLER, tool.descriptor(context()).access());
    assertEquals(org.saturn.app.agent.ToolEffect.ROOM_MESSAGE, tool.descriptor(context()).effect());
    assertEquals(
        org.saturn.app.agent.ToolResultMode.ROOM_DELIVERY_AND_MODEL_DATA,
        tool.descriptor(context()).resultMode());
    assertEquals(
        org.saturn.app.agent.ToolEffect.MODERATION, tool.descriptor(moderatorContext()).effect());
    assertEquals(
        org.saturn.app.agent.ToolAccess.CREATOR_ONLY, tool.descriptor(creatorContext()).access());
  }

  @Test
  void schemaToolIsAdminOnlyAndReturnsStructuredMetadata() {
    AgentDatabaseSchema schema =
        new AgentDatabaseSchema(
            List.of(
                new AgentDatabaseSchema.Table(
                    "messages",
                    List.of(
                        new AgentDatabaseSchema.Column(0, "trip", "TEXT", true, false),
                        new AgentDatabaseSchema.Column(1, "hash", "TEXT", true, false)),
                    List.of(),
                    List.of())));
    DatabaseSchemaTool tool =
        new DatabaseSchemaTool(() -> schema, AgentSqlConfig.from(new com.moandjiezana.toml.Toml()));
    AgentContext regular = context();
    AgentContext admin =
        new AgentContext(
            regular.room(),
            regular.nick(),
            regular.trip(),
            regular.hash(),
            regular.whisper(),
            regular.roomUsers(),
            Set.of(AgentCapability.DYNAMIC_SQL));

    assertFalse(tool.isAvailableTo(regular));
    assertTrue(tool.isAvailableTo(admin));
    assertTrue(tool.execute(regular, new JsonObject()).isError());
    AgentToolResult result = tool.execute(admin, new JsonObject());
    assertFalse(result.isError());
    assertEquals(
        "messages",
        com.google.gson.JsonParser.parseString(result.content())
            .getAsJsonObject()
            .getAsJsonArray("tables")
            .get(0)
            .getAsJsonObject()
            .get("name")
            .getAsString());
  }

  @Test
  void dynamicSqlToolValidatesInputAndReturnsStructuredRows() {
    AgentDatabaseSchema schema =
        new AgentDatabaseSchema(
            List.of(new AgentDatabaseSchema.Table("messages", List.of(), List.of(), List.of())));
    AgentSqlConfig config = AgentSqlConfig.from(new com.moandjiezana.toml.Toml());
    DatabaseSqlTool tool =
        new DatabaseSqlTool(
            () -> schema,
            (sql, ignoredSchema) -> new ValidatedAgentSql(sql, "fingerprint"),
            (sql, ignoredConfig) ->
                new AgentSqlResult(List.of("count"), List.of(List.of((Object) 42L)), false, 3),
            config);

    AgentToolResult missing = tool.execute(adminContext(), new JsonObject());
    JsonObject blankArguments = new JsonObject();
    blankArguments.addProperty("sql", "   ");
    AgentToolResult blank = tool.execute(adminContext(), blankArguments);
    JsonObject wrongTypeArguments = new JsonObject();
    wrongTypeArguments.add("sql", new JsonObject());
    AgentToolResult wrongType = tool.execute(adminContext(), wrongTypeArguments);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("sql", "SELECT count(*) FROM messages");
    AgentToolResult regularCaller = tool.execute(context(), arguments);
    AgentToolResult result = tool.execute(adminContext(), arguments);

    assertTrue(missing.isError());
    assertTrue(blank.isError());
    assertTrue(wrongType.isError());
    assertTrue(regularCaller.isError());
    assertEquals(
        AgentSqlErrorCode.EMPTY_SQL.name(),
        com.google.gson.JsonParser.parseString(missing.content())
            .getAsJsonObject()
            .get("code")
            .getAsString());
    assertEquals(AgentSqlErrorCode.EMPTY_SQL.name(), errorCode(blank));
    assertFalse(result.isError());
    assertEquals(
        42,
        com.google.gson.JsonParser.parseString(result.content())
            .getAsJsonObject()
            .getAsJsonArray("rows")
            .get(0)
            .getAsJsonArray()
            .get(0)
            .getAsInt());
    assertEquals(Set.of("database_schema"), tool.requiredSuccessfulTools());
  }

  @Test
  void dynamicSqlToolReturnsStablePolicyAndExecutionErrors() {
    AgentDatabaseSchema schema = new AgentDatabaseSchema(List.of());
    AgentSqlConfig config = AgentSqlConfig.from(new com.moandjiezana.toml.Toml());
    DatabaseSqlTool rejectedByPolicy =
        new DatabaseSqlTool(
            () -> schema,
            (sql, ignoredSchema) -> {
              throw new AgentSqlPolicyException(
                  AgentSqlErrorCode.FORBIDDEN_TABLE, "forbidden table");
            },
            (sql, ignoredConfig) -> {
              throw new AssertionError("Repository must not run rejected SQL");
            },
            config);
    DatabaseSqlTool failedExecution =
        new DatabaseSqlTool(
            () -> schema,
            (sql, ignoredSchema) -> new ValidatedAgentSql(sql, "fingerprint"),
            (sql, ignoredConfig) -> {
              throw new AgentPersistenceException(
                  AgentSqlErrorCode.TIMEOUT, "internal detail", null);
            },
            config);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("sql", "SELECT * FROM hidden");

    AgentToolResult policyError = rejectedByPolicy.execute(adminContext(), arguments);
    AgentToolResult executionError = failedExecution.execute(adminContext(), arguments);

    assertEquals(AgentSqlErrorCode.FORBIDDEN_TABLE.name(), errorCode(policyError));
    assertEquals(AgentSqlErrorCode.TIMEOUT.name(), errorCode(executionError));
    assertTrue(policyError.isError());
    assertTrue(executionError.isError());
  }

  @Test
  void dynamicSqlToolReturnsStableUnavailableAndRuntimeErrors() {
    AgentDatabaseSchema schema = new AgentDatabaseSchema(List.of());
    AgentSqlConfig disabledConfig =
        new AgentSqlConfig(false, 4_000, 50, 32, 2_000, 32_000, Duration.ofSeconds(1));
    DatabaseSqlTool disabled =
        new DatabaseSqlTool(
            () -> schema,
            (sql, ignoredSchema) -> new ValidatedAgentSql(sql, "fingerprint"),
            (sql, ignoredConfig) -> new AgentSqlResult(List.of(), List.of(), false, 0),
            disabledConfig);
    AgentSqlConfig enabledConfig = AgentSqlConfig.from(new com.moandjiezana.toml.Toml());
    DatabaseSqlTool runtimeFailure =
        new DatabaseSqlTool(
            () -> schema,
            (sql, ignoredSchema) -> new ValidatedAgentSql(sql, "fingerprint"),
            (sql, ignoredConfig) -> {
              throw new IllegalStateException("internal detail");
            },
            enabledConfig);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("sql", "SELECT 1");

    AgentToolResult unavailable = disabled.execute(adminContext(), arguments);
    AgentToolResult failed = runtimeFailure.execute(adminContext(), arguments);

    assertTrue(unavailable.isError());
    assertEquals("Tool is unavailable for this caller", unavailable.content());
    assertTrue(failed.isError());
    assertEquals(AgentSqlErrorCode.EXECUTION_FAILED.name(), errorCode(failed));
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

  private AgentContext moderatorContext() {
    return contextWithCapabilities(Set.of(AgentCapability.MODERATION_COMMANDS));
  }

  private AgentContext creatorContext() {
    return contextWithCapabilities(
        Set.of(
            AgentCapability.DYNAMIC_SQL,
            AgentCapability.MODERATION_COMMANDS,
            AgentCapability.PERMANENT_BAN));
  }

  private AgentContext contextWithCapabilities(Set<AgentCapability> capabilities) {
    AgentContext context = context();
    return new AgentContext(
        context.room(),
        context.nick(),
        context.trip(),
        context.hash(),
        context.whisper(),
        context.roomUsers(),
        capabilities);
  }

  private Set<String> commandEnum(RunCommandTool tool, AgentContext context) {
    return tool
        .parameters(context)
        .getAsJsonObject("properties")
        .getAsJsonObject("command")
        .getAsJsonArray("enum")
        .asList()
        .stream()
        .map(value -> value.getAsString())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private String errorCode(AgentToolResult result) {
    return com.google.gson.JsonParser.parseString(result.content())
        .getAsJsonObject()
        .get("code")
        .getAsString();
  }
}
