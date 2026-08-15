package org.saturn.app.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.AgentCapability;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentSqlConfig;
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
  void roomAndDatabaseToolsReturnStructuredData() {
    AgentContext context = context();
    RoomUsersTool roomUsers = new RoomUsersTool();
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
    arguments.addProperty("limit", 20);

    AgentToolResult missingNick = tool.execute(context(), new JsonObject());
    AgentToolResult result = tool.execute(context(), arguments);

    assertTrue(missingNick.isError());
    assertFalse(result.isError());
    assertEquals("recent_messages_for_user", queryName.get());
    assertEquals("jetty", queryArguments.get().get("nick").getAsString());
    assertTrue(result.content().contains("matched"));
  }

  @Test
  void commandToolAllowsReadOnlyCommandsAndRejectsRecursiveOrPrivilegedCommands() {
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

    assertFalse(tool.execute(context(), safe).isError());
    assertEquals("weather Chisinau", invoked.get());
    assertTrue(tool.execute(context(), recursive).isError());
    assertTrue(tool.execute(context(), privileged).isError());
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
    JsonObject wrongTypeArguments = new JsonObject();
    wrongTypeArguments.add("sql", new JsonObject());
    AgentToolResult wrongType = tool.execute(adminContext(), wrongTypeArguments);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("sql", "SELECT count(*) FROM messages");
    AgentToolResult regularCaller = tool.execute(context(), arguments);
    AgentToolResult result = tool.execute(adminContext(), arguments);

    assertTrue(missing.isError());
    assertTrue(wrongType.isError());
    assertTrue(regularCaller.isError());
    assertEquals(
        AgentSqlErrorCode.EMPTY_SQL.name(),
        com.google.gson.JsonParser.parseString(missing.content())
            .getAsJsonObject()
            .get("code")
            .getAsString());
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

  private String errorCode(AgentToolResult result) {
    return com.google.gson.JsonParser.parseString(result.content())
        .getAsJsonObject()
        .get("code")
        .getAsString();
  }
}
