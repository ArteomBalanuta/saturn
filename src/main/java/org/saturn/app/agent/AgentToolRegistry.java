package org.saturn.app.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.StringJoiner;
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
        .forEach(tool -> definitions.add(definition(tool, context)));
    return definitions;
  }

  private JsonObject definition(AgentTool tool, AgentContext context) {
    AgentToolDescriptor descriptor = tool.descriptor(context);
    JsonObject function = new JsonObject();
    function.addProperty("name", descriptor.name());
    function.addProperty("description", renderDescription(descriptor));
    function.add("parameters", descriptor.parameters().deepCopy());
    JsonObject wrapper = new JsonObject();
    wrapper.addProperty("type", "function");
    wrapper.add("function", function);
    return wrapper;
  }

  private String renderDescription(AgentToolDescriptor descriptor) {
    StringBuilder description = new StringBuilder(descriptor.description());
    description.append("\n\nSATURN SDK CONTRACT\n");
    appendLine(description, "label", descriptor.label());
    appendLine(description, "category", descriptor.category());
    appendLine(description, "access", descriptor.access());
    appendLine(description, "effect", descriptor.effect());
    appendLine(description, "result_mode", descriptor.resultMode());
    appendList(description, "when_to_use", descriptor.whenToUse());
    appendList(description, "when_not_to_use", descriptor.whenNotToUse());
    appendList(description, "required_capabilities", descriptor.requiredCapabilities());
    appendList(description, "required_successful_tools", descriptor.requiredSuccessfulTools());
    for (ToolExample example : descriptor.examples()) {
      description.append("example: ")
          .append(example.toolName())
          .append(' ')
          .append(example.arguments())
          .append(" - ")
          .append(example.purpose())
          .append('\n');
    }
    return description.toString().stripTrailing();
  }

  private void appendLine(StringBuilder description, String label, Object value) {
    description.append(label).append(": ").append(value).append('\n');
  }

  private void appendList(StringBuilder description, String label, Iterable<?> values) {
    StringJoiner joined = new StringJoiner(", ");
    values.forEach(value -> joined.add(String.valueOf(value)));
    if (joined.length() > 0) {
      appendLine(description, label, joined);
    }
  }
}
