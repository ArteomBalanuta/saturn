package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmToolCall;

class AgentToolExecutorTest {
  @Test
  void containsMalformedUnknownAndDuplicateCalls() {
    AtomicInteger executions = new AtomicInteger();
    AgentToolRegistry registry =
        new AgentToolRegistry().register(countingTool(executions)).freeze();
    AgentToolExecutor executor = new AgentToolExecutor(registry, config());

    assertTrue(executor.execute(null, new LlmToolCall("1", "missing", "{}")).isError());
    assertTrue(executor.execute(null, new LlmToolCall("2", "count", "{")).isError());
    assertFalse(executor.execute(null, new LlmToolCall("3", "count", "{\"value\":1}")).isError());
    assertTrue(executor.execute(null, new LlmToolCall("4", "count", "{\"value\":1}")).isError());
    assertEquals(1, executions.get());
  }

  @Test
  void rejectsNullArgumentsAndDeduplicatesEquivalentJsonObjects() {
    AtomicInteger executions = new AtomicInteger();
    AgentToolExecutor executor =
        new AgentToolExecutor(
            new AgentToolRegistry().register(countingTool(executions)).freeze(), config());

    assertTrue(executor.execute(null, new LlmToolCall("1", "count", "null")).isError());
    assertFalse(
        executor.execute(null, new LlmToolCall("2", "count", "{\"a\":1,\"b\":2}")).isError());
    assertTrue(
        executor.execute(null, new LlmToolCall("3", "count", "{\"b\":2,\"a\":1}")).isError());
    assertEquals(1, executions.get());
  }

  @Test
  void enforcesPerToolLimitAndDisablesRepeatedlyFailingTool() {
    AtomicInteger executions = new AtomicInteger();
    AgentTool failing =
        new AgentTool() {
          @Override
          public String name() {
            return "fail";
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            executions.incrementAndGet();
            throw new IllegalStateException("boom");
          }
        };
    AgentToolExecutor executor =
        new AgentToolExecutor(new AgentToolRegistry().register(failing).freeze(), config());

    assertTrue(executor.execute(null, new LlmToolCall("1", "fail", "{\"n\":1}")).isError());
    assertTrue(executor.execute(null, new LlmToolCall("2", "fail", "{\"n\":2}")).isError());
    AgentToolResult disabled = executor.execute(null, new LlmToolCall("3", "fail", "{\"n\":3}"));

    assertTrue(disabled.isError());
    assertTrue(disabled.content().contains("disabled"));
    assertEquals(2, executions.get());
  }

  @Test
  void requiresSuccessfulPrerequisiteWithinTheSameInvocation() {
    AtomicInteger sqlExecutions = new AtomicInteger();
    AgentTool schema = successfulTool("database_schema");
    AgentTool sql =
        new AgentTool() {
          @Override
          public String name() {
            return "database_sql";
          }

          @Override
          public Set<String> requiredSuccessfulTools() {
            return Set.of("database_schema");
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            sqlExecutions.incrementAndGet();
            return AgentToolResult.success(name(), arguments);
          }
        };
    AgentToolExecutor executor =
        new AgentToolExecutor(
            new AgentToolRegistry().register(schema).register(sql).freeze(), config());
    AgentContext context =
        new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());
    LlmToolCall sqlCall = new LlmToolCall("sql-1", "database_sql", "{\"sql\":\"SELECT 1\"}");

    AgentToolResult blocked = executor.execute(context, sqlCall);
    AgentToolResult described =
        executor.execute(context, new LlmToolCall("schema-1", "database_schema", "{}"));
    AgentToolResult executed = executor.execute(context, sqlCall);

    assertTrue(blocked.isError());
    assertTrue(blocked.content().contains("database_schema"));
    assertFalse(described.isError());
    assertFalse(executed.isError());
    assertEquals(1, sqlExecutions.get());
  }

  private AgentTool successfulTool(String name) {
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

  private AgentTool countingTool(AtomicInteger executions) {
    return new AgentTool() {
      @Override
      public String name() {
        return "count";
      }

      @Override
      public AgentToolResult execute(AgentContext context, JsonObject arguments) {
        executions.incrementAndGet();
        return AgentToolResult.success(name(), arguments);
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
