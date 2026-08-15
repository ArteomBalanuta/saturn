package org.saturn.app.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AgentToolRegistry {
  private final Map<String, AgentTool> tools = new LinkedHashMap<>();
  private boolean frozen;

  public AgentToolRegistry register(AgentTool tool) {
    if (frozen) {
      throw new IllegalStateException("Agent tool registry is frozen");
    }
    if (tools.putIfAbsent(tool.name(), tool) != null) {
      throw new IllegalArgumentException("Duplicate agent tool: " + tool.name());
    }
    return this;
  }

  public AgentToolRegistry freeze() {
    frozen = true;
    return this;
  }

  public Optional<AgentTool> find(AgentContext context, String name) {
    return Optional.ofNullable(tools.get(name)).filter(tool -> tool.isAvailableTo(context));
  }

  public JsonArray definitions(AgentContext context) {
    JsonArray definitions = new JsonArray();
    tools.values().stream()
        .filter(tool -> tool.isAvailableTo(context))
        .forEach(tool -> definitions.add(definition(tool)));
    return definitions;
  }

  private JsonObject definition(AgentTool tool) {
    JsonObject function = new JsonObject();
    function.addProperty("name", tool.name());
    function.addProperty("description", tool.description());
    function.add("parameters", tool.parameters().deepCopy());
    JsonObject wrapper = new JsonObject();
    wrapper.addProperty("type", "function");
    wrapper.add("function", function);
    return wrapper;
  }
}
