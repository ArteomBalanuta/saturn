package org.saturn.app.agent;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.StringJoiner;

/** Converts a validated Saturn SDK descriptor into the provider's function-tool payload. */
/** Serializes validated tool descriptors into OpenAI-compatible function definitions. */
public final class AgentToolDefinitionFactory {
  /** Creates a provider payload without changing the descriptor's contract. */
  public JsonObject create(AgentToolDescriptor descriptor) {
    JsonObject function = new JsonObject();
    function.addProperty("name", descriptor.name());
    function.addProperty("description", renderDescription(descriptor));
    function.add("parameters", descriptor.parameters().deepCopy());

    JsonObject definition = new JsonObject();
    definition.addProperty("type", "function");
    definition.add("function", function);
    return definition;
  }

  private String renderDescription(AgentToolDescriptor descriptor) {
    StringBuilder description = new StringBuilder(descriptor.description());
    description.append("\n\nSATURN SDK CONTRACT\n");
    appendLine(description, "label", descriptor.label());
    appendLine(description, "category", descriptor.category());
    appendLine(description, "access", descriptor.access());
    appendLine(description, "effect", descriptor.effect());
    appendLine(description, "read_only", descriptor.isReadOnly());
    appendLine(description, "result_mode", descriptor.resultMode());
    appendLine(description, "idempotent", descriptor.isIdempotent());
    appendLine(description, "timeout_ms", timeoutMillis(descriptor.timeout()));
    appendLine(description, "result_schema", descriptor.resultSchema());
    appendList(description, "when_to_use", descriptor.whenToUse());
    appendList(description, "when_not_to_use", descriptor.whenNotToUse());
    appendList(description, "required_capabilities", descriptor.requiredCapabilities());
    appendList(description, "required_successful_tools", descriptor.requiredSuccessfulTools());
    for (ToolExample example : descriptor.examples()) {
      description
          .append("example: ")
          .append(example.toolName())
          .append(' ')
          .append(example.arguments())
          .append(" - ")
          .append(example.purpose())
          .append('\n');
    }
    return description.toString().stripTrailing();
  }

  private long timeoutMillis(Duration timeout) {
    return timeout.isZero() ? 0 : timeout.toMillis();
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
