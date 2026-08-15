package org.saturn.app.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the immutable set of tools available to the agent runtime.
 *
 * <p>Registration occurs during startup. After {@link #freeze()}, concurrent lookups and provider
 * definition generation are read-only.
 */
public final class AgentToolRegistry {
  private final Map<String, AgentTool> tools = new LinkedHashMap<>();
  private final AgentToolDefinitionFactory definitionFactory = new AgentToolDefinitionFactory();
  private boolean frozen;

  /** Registers a uniquely named tool before the registry is frozen. */
  public AgentToolRegistry register(AgentTool tool) {
    if (frozen) {
      throw new IllegalStateException("Agent tool registry is frozen");
    }
    if (tools.putIfAbsent(tool.name(), tool) != null) {
      throw new IllegalArgumentException("Duplicate agent tool: " + tool.name());
    }
    return this;
  }

  /** Prevents further registration and returns this registry for startup wiring. */
  public AgentToolRegistry freeze() {
    frozen = true;
    return this;
  }

  /** Resolves a tool only when it is available to the supplied caller context. */
  public Optional<AgentTool> find(AgentContext context, String name) {
    return Optional.ofNullable(tools.get(name)).filter(tool -> tool.isAvailableTo(context));
  }

  /** Returns provider definitions for every tool visible to the supplied caller context. */
  public JsonArray definitions(AgentContext context) {
    JsonArray definitions = new JsonArray();
    tools.values().stream()
        .filter(tool -> tool.isAvailableTo(context))
        .forEach(tool -> definitions.add(definition(tool, context)));
    return definitions;
  }

  private JsonObject definition(AgentTool tool, AgentContext context) {
    AgentToolDescriptor descriptor = tool.descriptor(context);
    if (!tool.name().equals(descriptor.name())) {
      throw new IllegalStateException(
          "Tool descriptor name does not match registered tool: " + tool.name());
    }
    return definitionFactory.create(descriptor);
  }
}
