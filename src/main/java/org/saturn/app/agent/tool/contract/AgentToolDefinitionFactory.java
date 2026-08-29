package org.saturn.app.agent.tool.contract;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.StringJoiner;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.api.ToolExample;

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

  /**
   * Implements the {@code renderDescription} operation for this agent component.
   *
   * @param descriptor input argument used by this operation
   * @return the operation result
   */
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
    appendSortedList(description, "required_capabilities", descriptor.requiredCapabilities());
    appendSortedList(
        description, "required_successful_tools", descriptor.requiredSuccessfulTools());
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

  /**
   * Implements the {@code timeoutMillis} operation for this agent component.
   *
   * @param timeout input argument used by this operation
   * @return the operation result
   */
  private long timeoutMillis(Duration timeout) {
    return timeout.isZero() ? 0 : timeout.toMillis();
  }

  /**
   * Implements the {@code appendLine} operation for this agent component.
   *
   * @param description input argument used by this operation
   * @param label input argument used by this operation
   * @param value input argument used by this operation
   */
  private void appendLine(StringBuilder description, String label, Object value) {
    description.append(label).append(": ").append(value).append('\n');
  }

  /**
   * Implements the {@code appendList} operation for this agent component.
   *
   * @param description input argument used by this operation
   * @param label input argument used by this operation
   * @param values input argument used by this operation
   */
  private void appendList(StringBuilder description, String label, Iterable<?> values) {
    StringJoiner joined = new StringJoiner(", ");
    values.forEach(value -> joined.add(String.valueOf(value)));
    if (joined.length() > 0) {
      appendLine(description, label, joined);
    }
  }

  /**
   * Implements the {@code appendSortedList} operation for this agent component.
   *
   * @param description input argument used by this operation
   * @param label input argument used by this operation
   * @param values input argument used by this operation
   */
  private void appendSortedList(StringBuilder description, String label, Iterable<?> values) {
    appendList(
        description,
        label,
        java.util.stream.StreamSupport.stream(values.spliterator(), false)
            .map(String::valueOf)
            .sorted()
            .toList());
  }
}
