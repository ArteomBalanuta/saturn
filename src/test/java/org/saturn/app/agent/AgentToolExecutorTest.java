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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

  @Test
  void rejectsArgumentsOutsideThePublishedSchemaConstraints() {
    AtomicInteger executions = new AtomicInteger();
    AgentTool constrained =
        new AgentTool() {
          @Override
          public String name() {
            return "constrained";
          }

          @Override
          public JsonObject parameters() {
            JsonObject mode = new JsonObject();
            mode.addProperty("type", "string");
            mode.add("enum", new com.google.gson.JsonArray());
            mode.getAsJsonArray("enum").add("brief");
            JsonObject limit = new JsonObject();
            limit.addProperty("type", "integer");
            limit.addProperty("minimum", 1);
            limit.addProperty("maximum", 3);
            JsonObject name = new JsonObject();
            name.addProperty("type", "string");
            name.addProperty("minLength", 2);
            name.addProperty("maxLength", 4);
            JsonObject properties = new JsonObject();
            properties.add("mode", mode);
            properties.add("limit", limit);
            properties.add("name", name);
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.addProperty("additionalProperties", false);
            return schema;
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            executions.incrementAndGet();
            return AgentToolResult.success(name(), arguments);
          }
        };
    AgentToolExecutor executor =
        new AgentToolExecutor(new AgentToolRegistry().register(constrained).freeze(), config());

    assertFalse(
        executor
            .execute(
                null,
                new LlmToolCall(
                    "valid", "constrained", "{\"mode\":\"brief\",\"limit\":2,\"name\":\"name\"}"))
            .isError());
    assertTrue(
        executor
            .execute(null, new LlmToolCall("enum", "constrained", "{\"mode\":\"long\"}"))
            .isError());
    assertTrue(
        executor
            .execute(null, new LlmToolCall("minimum", "constrained", "{\"limit\":0}"))
            .isError());
    assertTrue(
        executor
            .execute(null, new LlmToolCall("length", "constrained", "{\"name\":\"a\"}"))
            .isError());
    assertTrue(
        executor
            .execute(null, new LlmToolCall("unknown", "constrained", "{\"unknown\":true}"))
            .isError());
    assertEquals(1, executions.get());
  }

  @Test
  void returnsATimeoutEnvelopeWhenAToolExceedsItsDeadline() {
    AgentTool slowTool =
        new AgentTool() {
          @Override
          public String name() {
            return "slow";
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            try {
              Thread.sleep(Duration.ofSeconds(1));
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            }
            return AgentToolResult.success(name(), "finished");
          }
        };

    try (AgentToolExecutor executor =
        new AgentToolExecutor(
            new AgentToolRegistry().register(slowTool).freeze(), config(Duration.ofMillis(20)))) {
      AgentToolResult result = executor.execute(null, new LlmToolCall("slow-1", "slow", "{}"));

      assertTrue(result.isError());
      assertEquals("TOOL_TIMEOUT", result.errorCode());
      assertTrue(result.envelopeJson().contains("TOOL_TIMEOUT"));
    }
  }

  @Test
  void convertsNullToolOutputToACodedFailure() {
    AgentTool nullTool =
        new AgentTool() {
          @Override
          public String name() {
            return "null_result";
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return null;
          }
        };

    try (AgentToolExecutor executor =
        new AgentToolExecutor(new AgentToolRegistry().register(nullTool).freeze(), config())) {
      AgentToolResult result =
          executor.execute(null, new LlmToolCall("null-1", "null_result", "{}"));

      assertTrue(result.isError());
      assertEquals("TOOL_EXECUTION_FAILED", result.errorCode());
      assertEquals("Tool execution failed", result.content());
    }
  }

  @Test
  void closeInterruptsAnInFlightToolExecution() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch interrupted = new CountDownLatch(1);
    AgentTool blockingTool =
        new AgentTool() {
          @Override
          public String name() {
            return "blocking";
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            started.countDown();
            try {
              Thread.sleep(Duration.ofHours(1));
            } catch (InterruptedException exception) {
              interrupted.countDown();
              Thread.currentThread().interrupt();
            }
            return AgentToolResult.success(name(), "stopped");
          }
        };

    try (AgentToolExecutor executor =
        new AgentToolExecutor(
            new AgentToolRegistry().register(blockingTool).freeze(), config(Duration.ofHours(1)))) {
      Thread caller =
          Thread.startVirtualThread(
              () -> executor.execute(null, new LlmToolCall("blocking-1", "blocking", "{}")));

      assertTrue(started.await(1, TimeUnit.SECONDS));
      executor.close();
      assertTrue(interrupted.await(1, TimeUnit.SECONDS));
      caller.join(Duration.ofSeconds(1));
    }
  }

  @Test
  void rejectsSuccessfulToolOutputThatViolatesItsPublishedResultSchema() {
    AgentTool invalidResultTool =
        new AgentTool() {
          @Override
          public String name() {
            return "structured";
          }

          @Override
          public AgentToolDescriptor descriptor(AgentContext context) {
            JsonObject resultSchema = new JsonObject();
            resultSchema.addProperty("type", "object");
            JsonObject resultProperties = new JsonObject();
            JsonObject answerProperty = new JsonObject();
            answerProperty.addProperty("type", "string");
            resultProperties.add("answer", answerProperty);
            resultSchema.add("properties", resultProperties);
            com.google.gson.JsonArray required = new com.google.gson.JsonArray();
            required.add("answer");
            resultSchema.add("required", required);
            return new AgentToolDescriptor(
                name(),
                "Structured",
                "Returns a structured answer. Do NOT use for room delivery.",
                "test",
                ToolAccess.PUBLIC,
                ToolEffect.READ_ONLY,
                ToolResultMode.MODEL_DATA,
                parameters(),
                List.of("Read structured test data."),
                List.of("Do not deliver chat messages."),
                List.of(),
                Set.of(),
                Set.of(),
                true,
                Duration.ZERO,
                resultSchema);
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), "not-json");
          }
        };

    try (AgentToolExecutor executor =
        new AgentToolExecutor(
            new AgentToolRegistry().register(invalidResultTool).freeze(), config())) {
      AgentToolResult result =
          executor.execute(null, new LlmToolCall("structured-1", "structured", "{}"));

      assertTrue(result.isError());
      assertEquals("INVALID_TOOL_RESULT", result.errorCode());
    }
  }

  @Test
  void runsConsecutiveIdempotentReadToolsConcurrentlyAndPreservesObservationOrder()
      throws InterruptedException {
    CountDownLatch started = new CountDownLatch(2);
    AgentTool first = concurrentReadTool("first", started);
    AgentTool second = concurrentReadTool("second", started);

    try (AgentToolExecutor executor =
        new AgentToolExecutor(
            new AgentToolRegistry().register(first).register(second).freeze(), config())) {
      List<AgentToolResult> results =
          executor.executeAll(
              null,
              List.of(
                  new LlmToolCall("first-call", "first", "{}"),
                  new LlmToolCall("second-call", "second", "{}")));

      assertEquals(
          List.of("first", "second"), results.stream().map(AgentToolResult::toolName).toList());
      assertTrue(started.await(10, TimeUnit.MILLISECONDS));
      assertFalse(results.stream().anyMatch(AgentToolResult::isError));
    }
  }

  @Test
  void resolvesEachToolDescriptorOnceWhenExecutingAProviderBatch() {
    AtomicInteger descriptorCalls = new AtomicInteger();
    AgentTool delegate = successfulTool("described");
    AgentTool tool =
        new AgentTool() {
          @Override
          public String name() {
            return delegate.name();
          }

          @Override
          public AgentToolDescriptor descriptor(AgentContext context) {
            descriptorCalls.incrementAndGet();
            return delegate.descriptor(context);
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return delegate.execute(context, arguments);
          }
        };

    try (AgentToolExecutor executor =
        new AgentToolExecutor(new AgentToolRegistry().register(tool).freeze(), config())) {
      AgentToolResult result =
          executor
              .executeAll(null, List.of(new LlmToolCall("described-1", "described", "{}")))
              .getFirst();

      assertFalse(result.isError());
      assertEquals(1, descriptorCalls.get());
    }
  }

  @Test
  void runsCommandToolsOnlyAfterThePrecedingReadBatchCompletes() {
    List<String> events = new CopyOnWriteArrayList<>();
    CountDownLatch readsStarted = new CountDownLatch(2);
    AgentTool first = orderedReadTool("first", readsStarted, events);
    AgentTool second = orderedReadTool("second", readsStarted, events);
    AgentTool command = orderedCommandTool(events);

    try (AgentToolExecutor executor =
        new AgentToolExecutor(
            new AgentToolRegistry().register(first).register(second).register(command).freeze(),
            config())) {
      List<AgentToolResult> results =
          executor.executeAll(
              null,
              List.of(
                  new LlmToolCall("first-call", "first", "{}"),
                  new LlmToolCall("second-call", "second", "{}"),
                  new LlmToolCall("command-call", "command", "{}")));

      assertFalse(results.stream().anyMatch(AgentToolResult::isError));
      assertEquals("command", events.getLast());
    }
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

  private AgentTool concurrentReadTool(String name, CountDownLatch started) {
    return new AgentTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public AgentToolDescriptor descriptor(AgentContext context) {
        return new AgentToolDescriptor(
            name(),
            name(),
            "Reads test data. Do NOT use when an action is required.",
            "test",
            ToolAccess.PUBLIC,
            ToolEffect.READ_ONLY,
            ToolResultMode.MODEL_DATA,
            parameters(),
            List.of("Read independent test data."),
            List.of("Do not use for room actions."),
            List.of(),
            Set.of(),
            Set.of(),
            true,
            Duration.ZERO,
            anyResultSchema());
      }

      @Override
      public AgentToolResult execute(AgentContext context, JsonObject arguments) {
        started.countDown();
        try {
          if (!started.await(250, TimeUnit.MILLISECONDS)) {
            return AgentToolResult.error(null, name(), "The other read did not start");
          }
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          return AgentToolResult.error(null, name(), "Read was interrupted");
        }
        return AgentToolResult.success(name(), name());
      }
    };
  }

  private AgentTool orderedReadTool(String name, CountDownLatch started, List<String> events) {
    AgentTool delegate = concurrentReadTool(name, started);
    return new AgentTool() {
      @Override
      public String name() {
        return delegate.name();
      }

      @Override
      public AgentToolDescriptor descriptor(AgentContext context) {
        return delegate.descriptor(context);
      }

      @Override
      public AgentToolResult execute(AgentContext context, JsonObject arguments) {
        AgentToolResult result = delegate.execute(context, arguments);
        events.add(name);
        return result;
      }
    };
  }

  private AgentTool orderedCommandTool(List<String> events) {
    return new AgentTool() {
      @Override
      public String name() {
        return "command";
      }

      @Override
      public AgentToolDescriptor descriptor(AgentContext context) {
        return new AgentToolDescriptor(
            name(),
            "Command",
            "Runs a test command. Do NOT run in parallel.",
            "test",
            ToolAccess.PUBLIC,
            ToolEffect.ROOM_MESSAGE,
            ToolResultMode.ROOM_DELIVERY,
            parameters(),
            List.of("Run the ordered test action."),
            List.of("Do not run in parallel."),
            List.of(),
            Set.of(),
            Set.of(),
            false,
            Duration.ZERO,
            anyResultSchema());
      }

      @Override
      public AgentToolResult execute(AgentContext context, JsonObject arguments) {
        if (events.size() != 2) {
          return AgentToolResult.error(null, name(), "Reads were not complete");
        }
        events.add(name());
        return AgentToolResult.success(name(), "done");
      }
    };
  }

  private JsonObject anyResultSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "any");
    return schema;
  }

  private AgentConfig config() {
    return config(Duration.ofSeconds(1));
  }

  private AgentConfig config(Duration timeout) {
    return new AgentConfig(
        true,
        URI.create("http://localhost"),
        Optional.empty(),
        "",
        timeout,
        1,
        4,
        2,
        2,
        100,
        100,
        2,
        Duration.ofHours(1),
        0,
        Duration.ZERO,
        768,
        false,
        8,
        4,
        timeout);
  }
}
