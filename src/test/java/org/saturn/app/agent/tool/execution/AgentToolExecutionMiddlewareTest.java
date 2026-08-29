package org.saturn.app.agent.tool.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.agent.llm.LlmToolCall;

class AgentToolExecutionMiddlewareTest {
  @Test
  void executorConstructionRunsMiddlewareOnceAndIsolatesObservers() {
    AtomicInteger sideEffects = new AtomicInteger();
    AtomicInteger observerCalls = new AtomicInteger();
    AgentTool tool =
        new AgentTool() {
          @Override
          public String name() {
            return "integrated";
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            sideEffects.incrementAndGet();
            return AgentToolResult.success(name(), "done");
          }
        };
    AgentToolExecutionMiddleware middleware =
        (context, ignoredTool, arguments, continuation) -> {
          AgentToolResult result = continuation.invoke();
          assertThrows(IllegalStateException.class, continuation::invoke);
          return result;
        };
    AgentToolExecutionObserver throwingObserver =
        (context, result) -> {
          throw new IllegalStateException("observer");
        };
    AgentToolExecutionObserver recordingObserver =
        (context, result) -> observerCalls.incrementAndGet();

    try (AgentToolExecutor executor =
        new AgentToolExecutor(
            new AgentToolRegistry().register(tool).freeze(),
            config(),
            Set.of(),
            List.of(middleware),
            List.of(throwingObserver, recordingObserver))) {
      AgentToolResult result =
          executor
              .executeAll(null, List.of(new LlmToolCall("integrated-1", "integrated", "{}")))
              .getFirst();

      assertEquals("done", result.content());
      assertEquals(1, sideEffects.get());
      assertEquals(1, observerCalls.get());
    }
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
        Duration.ZERO,
        768,
        false,
        8,
        4,
        Duration.ofSeconds(1));
  }
}
