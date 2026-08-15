package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentToolRegistryTest {
  @Test
  void freezesCatalogAndBuildsOpenAiDefinitions() {
    AgentToolRegistry registry = new AgentToolRegistry().register(tool("echo")).freeze();

    assertEquals(
        "echo",
        registry
            .definitions(context(Set.of()))
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("function")
            .get("name")
            .getAsString());
    assertThrows(IllegalStateException.class, () -> registry.register(tool("late")));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AgentToolRegistry().register(tool("echo")).register(tool("echo")));
  }

  @Test
  void hidesCapabilityRestrictedToolsFromDefinitionsAndLookup() {
    AgentTool dynamicSql =
        new AgentTool() {
          @Override
          public String name() {
            return "database_sql";
          }

          @Override
          public boolean isAvailableTo(AgentContext context) {
            return context.hasCapability(AgentCapability.DYNAMIC_SQL);
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), arguments);
          }
        };
    AgentToolRegistry registry = new AgentToolRegistry().register(dynamicSql).freeze();
    AgentContext regular = context(Set.of());
    AgentContext admin = context(Set.of(AgentCapability.DYNAMIC_SQL));

    assertEquals(0, registry.definitions(regular).size());
    assertTrue(registry.find(regular, "database_sql").isEmpty());
    assertEquals(1, registry.definitions(admin).size());
    assertTrue(registry.find(admin, "database_sql").isPresent());
    assertFalse(regular.hasCapability(AgentCapability.DYNAMIC_SQL));
  }

  @Test
  void serializesSdkContractAlongsideProviderDefinition() {
    AgentTool tool =
        new AgentTool() {
          @Override
          public String name() {
            return "weather";
          }

          @Override
          public AgentToolDescriptor descriptor(AgentContext context) {
            return new AgentToolDescriptor(
                name(),
                "Weather lookup",
                "Fetch the current weather for a location.",
                "information",
                ToolAccess.PUBLIC,
                ToolEffect.READ_ONLY,
                ToolResultMode.MODEL_DATA,
                parameters(context),
                List.of("Use when the user asks for current weather."),
                List.of("Do not use for historical climate analysis."),
                List.of(new ToolExample(name(), "{\"location\":\"Tokyo\"}", "Get Tokyo weather")),
                Set.of(),
                Set.of());
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), arguments);
          }
        };
    JsonObject function =
        new AgentToolRegistry()
            .register(tool)
            .definitions(context(Set.of()))
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("function");

    String description = function.get("description").getAsString();
    assertTrue(description.contains("SATURN SDK CONTRACT"));
    assertTrue(description.contains("label: Weather lookup"));
    assertTrue(description.contains("category: information"));
    assertTrue(description.contains("access: PUBLIC"));
    assertTrue(description.contains("effect: READ_ONLY"));
    assertTrue(description.contains("result_mode: MODEL_DATA"));
    assertTrue(description.contains("when_to_use: Use when the user asks for current weather."));
    assertTrue(description.contains("when_not_to_use: Do not use for historical climate analysis."));
    assertTrue(description.contains("example: weather {\"location\":\"Tokyo\"} - Get Tokyo weather"));
  }

  private AgentContext context(Set<AgentCapability> capabilities) {
    return new AgentContext(
        "programming", "alice", "trip-a", "hash-a", false, List.of("alice"), capabilities);
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
}
