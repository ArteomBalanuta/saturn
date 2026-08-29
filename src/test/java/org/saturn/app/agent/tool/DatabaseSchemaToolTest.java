package org.saturn.app.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentCapability;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.config.AgentSqlConfig;
import org.saturn.app.agent.persistence.AgentDatabaseSchema;
import org.saturn.app.agent.persistence.AgentSchemaRepository;

class DatabaseSchemaToolTest {
  @Test
  void rejectsUnavailableCallersBeforeRepositoryAccess() {
    DatabaseSchemaTool tool =
        new DatabaseSchemaTool(() -> new AgentDatabaseSchema(List.of()), config(true));

    AgentToolResult result = tool.execute(context(Set.of()), new JsonObject());

    assertTrue(result.isError());
    assertEquals("Tool is unavailable for this caller", result.content());
  }

  @Test
  void returnsSchemaForAuthorizedCaller() {
    DatabaseSchemaTool tool =
        new DatabaseSchemaTool(() -> new AgentDatabaseSchema(List.of()), config(true));

    AgentToolResult result =
        tool.execute(context(Set.of(AgentCapability.DYNAMIC_SQL)), new JsonObject());

    assertFalse(result.isError());
    assertEquals("{\"tables\":[]}", result.content());
  }

  @Test
  void mapsRepositoryFailureToStableError() {
    AgentSchemaRepository repository =
        () -> {
          throw new IllegalStateException("database details");
        };
    DatabaseSchemaTool tool = new DatabaseSchemaTool(repository, config(true));

    AgentToolResult result =
        tool.execute(context(Set.of(AgentCapability.DYNAMIC_SQL)), new JsonObject());

    assertTrue(result.isError());
    assertEquals("Database schema inspection failed", result.content());
  }

  private static AgentContext context(Set<AgentCapability> capabilities) {
    return new AgentContext("room", "nick", null, null, false, List.of(), capabilities);
  }

  private static AgentSqlConfig config(boolean enabled) {
    return new AgentSqlConfig(enabled, 100, 10, 5, 100, 1_000, Duration.ofSeconds(1));
  }
}
