package org.saturn.app.agent.tool.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.api.ToolAccess;
import org.saturn.app.agent.api.ToolEffect;
import org.saturn.app.agent.api.ToolResultMode;
import org.saturn.app.agent.tool.contract.AgentToolSchemas;

class AgentToolRegistryTest {
  @Test
  void tracksGenerationOnlyForSuccessfulMutations() {
    AgentToolRegistry registry = new AgentToolRegistry();
    assertEquals(0, registry.generation());
    registry.register(tool("echo"));
    assertEquals(1, registry.generation());
    assertThrows(IllegalArgumentException.class, () -> registry.register(tool("echo")));
    assertEquals(1, registry.generation());
    registry.freeze();
    assertEquals(1, registry.generation());
  }

  @Test
  void snapshotsAreImmutableAndIsolatedFromLaterMutation() {
    AgentToolRegistry registry = new AgentToolRegistry().register(tool("echo"));
    AgentToolRegistry.Snapshot before = registry.snapshot();

    assertEquals(1, before.generation());
    assertThrows(
        UnsupportedOperationException.class, () -> before.tools().put("other", tool("other")));
    registry.enableDynamicMode().replace(tool("other"));

    assertEquals(List.of("echo"), List.copyOf(before.tools().keySet()));
    assertEquals(List.of("echo", "other"), List.copyOf(registry.snapshot().tools().keySet()));
  }

  @Test
  void dynamicMutationIsAtomicAndFrozenByDefault() {
    AgentToolRegistry registry = new AgentToolRegistry().register(tool("echo")).freeze();
    assertThrows(IllegalStateException.class, () -> registry.replace(tool("other")));
    assertThrows(IllegalStateException.class, () -> registry.deregister("echo"));
    assertEquals(1, registry.generation());

    registry.enableDynamicMode().replace(tool("other"));
    assertEquals(2, registry.generation());
    assertTrue(registry.find(context("room"), "other").isPresent());
    assertTrue(registry.find(context("room"), "echo").isPresent());
    registry.deregister("echo");
    assertEquals(3, registry.generation());
    assertTrue(registry.find(context("room"), "echo").isEmpty());
    registry.deregister("missing");
    assertEquals(3, registry.generation());
    assertThrows(NullPointerException.class, () -> registry.deregister(null));
    assertThrows(IllegalArgumentException.class, () -> registry.deregister("Invalid"));
    assertEquals(3, registry.generation());
  }

  @Test
  void contextualDefinitionCacheDoesNotCrossContextsOrGenerations() {
    AgentTool contextual =
        new AgentTool() {
          @Override
          public String name() {
            return "contextual";
          }

          @Override
          public boolean isAvailableTo(AgentContext context) {
            return true;
          }

          @Override
          public AgentToolDescriptor descriptor(AgentContext context) {
            return new AgentToolDescriptor(
                name(),
                name(),
                "room=" + context.room(),
                "test",
                ToolAccess.PUBLIC,
                ToolEffect.READ_ONLY,
                ToolResultMode.MODEL_DATA,
                AgentToolSchemas.object(),
                List.of(),
                List.of("Do not use outside the caller room."),
                List.of(),
                Set.of(),
                Set.of());
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), arguments);
          }
        };
    AgentToolRegistry registry = new AgentToolRegistry().register(contextual).freeze();
    assertEquals(
        "room=one",
        registry
            .definitions(context("one"))
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("function")
            .get("description")
            .getAsString()
            .split("\\n")[0]);
    assertEquals(
        "room=two",
        registry
            .definitions(context("two"))
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("function")
            .get("description")
            .getAsString()
            .split("\\n")[0]);
  }

  @Test
  void concurrentReadersSeeCompleteSnapshotsDuringMutation() throws Exception {
    AgentToolRegistry registry =
        new AgentToolRegistry().register(tool("echo")).freeze().enableDynamicMode();
    try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
      Future<?> writer =
          pool.submit(
              () -> {
                for (int i = 0; i < 20; i++) registry.replace(tool("tool" + i));
              });
      for (int i = 0; i < 100; i++) assertTrue(registry.snapshot().tools().containsKey("echo"));
      writer.get();
    }
  }

  @Test
  void rejectsNullInvalidAndDuplicateRegistrations() {
    AgentToolRegistry registry = new AgentToolRegistry();

    assertThrows(NullPointerException.class, () -> registry.register(null));
    assertThrows(IllegalArgumentException.class, () -> registry.register(tool("")));
    assertThrows(IllegalArgumentException.class, () -> registry.register(tool("Invalid")));
    assertThrows(IllegalArgumentException.class, () -> registry.register(tool("tool-name")));
    assertThrows(IllegalArgumentException.class, () -> registry.register(tool("tool".repeat(17))));

    registry.register(tool("echo"));
    assertThrows(IllegalArgumentException.class, () -> registry.register(tool("echo")));
  }

  @Test
  void freezePreventsMutationAndIsIdempotent() {
    AgentToolRegistry registry = new AgentToolRegistry().register(tool("echo"));

    assertEquals(registry, registry.freeze());
    assertEquals(registry, registry.freeze());
    assertThrows(IllegalStateException.class, () -> registry.register(tool("other")));
  }

  @Test
  void lookupAndDefinitionsRespectContextAvailability() {
    AgentTool visible = tool("visible");
    AgentTool hidden =
        new AgentTool() {
          @Override
          public String name() {
            return "hidden";
          }

          @Override
          public boolean isAvailableTo(AgentContext context) {
            return "allowed-room".equals(context.room());
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), arguments);
          }
        };
    AgentToolRegistry registry =
        new AgentToolRegistry().register(visible).register(hidden).freeze();
    AgentContext allowed = new AgentContext("allowed-room", "nick", null, null, false, List.of());
    AgentContext denied = new AgentContext("denied-room", "nick", null, null, false, List.of());

    assertTrue(registry.find(allowed, "hidden").isPresent());
    assertTrue(registry.find(denied, "hidden").isEmpty());
    assertTrue(registry.find(allowed, "missing").isEmpty());

    JsonArray allowedDefinitions = registry.definitions(allowed);
    JsonArray deniedDefinitions = registry.definitions(denied);
    assertEquals(2, allowedDefinitions.size());
    assertEquals(1, deniedDefinitions.size());
    assertEquals("visible", functionName(deniedDefinitions.get(0).getAsJsonObject()));
    assertEquals("visible", functionName(allowedDefinitions.get(0).getAsJsonObject()));
    assertEquals("hidden", functionName(allowedDefinitions.get(1).getAsJsonObject()));
  }

  @Test
  void rejectsAContextualDescriptorWithAMismatchedName() {
    AgentTool mismatched =
        new AgentTool() {
          @Override
          public String name() {
            return "registered";
          }

          @Override
          public AgentToolDescriptor descriptor(AgentContext context) {
            return AgentToolRegistryTest.descriptor("different");
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), arguments);
          }
        };
    AgentToolRegistry registry = new AgentToolRegistry().register(mismatched).freeze();

    assertThrows(
        IllegalStateException.class,
        () -> registry.definitions(new AgentContext("room", "nick", null, null, false, List.of())));
  }

  @Test
  void returnsIndependentDefinitionSnapshots() {
    AgentToolRegistry registry = new AgentToolRegistry().register(tool("echo")).freeze();
    AgentContext context = new AgentContext("room", "nick", null, null, false, List.of());

    JsonArray first = registry.definitions(context);
    first.get(0).getAsJsonObject().getAsJsonObject("function").addProperty("name", "mutated");

    JsonArray second = registry.definitions(context);

    assertEquals("echo", functionName(second.get(0).getAsJsonObject()));
  }

  @Test
  void failsClosedWhenAvailabilityCheckThrows() {
    AgentTool unavailable =
        new AgentTool() {
          @Override
          public String name() {
            return "unstable";
          }

          @Override
          public boolean isAvailableTo(AgentContext context) {
            throw new IllegalStateException("availability backend unavailable");
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), arguments);
          }
        };
    AgentToolRegistry registry = new AgentToolRegistry().register(unavailable).freeze();
    AgentContext context = new AgentContext("room", "nick", null, null, false, List.of());

    assertTrue(registry.find(context, "unstable").isEmpty());
    assertTrue(registry.definitions(context).isEmpty());
  }

  private static String functionName(JsonObject definition) {
    return definition.getAsJsonObject("function").get("name").getAsString();
  }

  private static AgentContext context(String room) {
    return new AgentContext(room, "nick", null, null, false, List.of());
  }

  private static AgentTool tool(String name) {
    return new AgentTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public AgentToolResult execute(AgentContext context, JsonObject arguments) {
        return AgentToolResult.success(name(), arguments);
      }
    };
  }

  private static AgentToolDescriptor descriptor(String name) {
    return new AgentToolDescriptor(
        name,
        name,
        name,
        "test",
        ToolAccess.PUBLIC,
        ToolEffect.READ_ONLY,
        ToolResultMode.MODEL_DATA,
        AgentToolSchemas.object(),
        List.of(),
        List.of("Do not use for unrelated work."),
        List.of(),
        Set.of(),
        Set.of());
  }
}
