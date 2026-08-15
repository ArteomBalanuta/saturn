package org.saturn.app.agent;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record AgentToolDescriptor(
    String name,
    String label,
    String description,
    String category,
    ToolAccess access,
    ToolEffect effect,
    ToolResultMode resultMode,
    JsonObject parameters,
    List<String> whenToUse,
    List<String> whenNotToUse,
    List<ToolExample> examples,
    Set<String> requiredCapabilities,
    Set<String> requiredSuccessfulTools,
    boolean isIdempotent,
    Duration timeout,
    JsonObject resultSchema) {
  public AgentToolDescriptor {
    name = required(name, "name");
    if (!name.matches("[a-z][a-z0-9_]{0,63}")) {
      throw new IllegalArgumentException("name must be a lowercase alphanumeric identifier");
    }
    label = required(label, "label");
    description = required(description, "description");
    category = required(category, "category");
    access = Objects.requireNonNull(access, "access");
    effect = Objects.requireNonNull(effect, "effect");
    resultMode = Objects.requireNonNull(resultMode, "resultMode");
    parameters = Objects.requireNonNull(parameters, "parameters").deepCopy();
    AgentToolSchemaValidator.validateSchema(parameters);
    whenToUse = immutableList(whenToUse, "whenToUse");
    whenNotToUse = immutableList(whenNotToUse, "whenNotToUse");
    if (whenNotToUse.isEmpty()) {
      throw new IllegalArgumentException("whenNotToUse must declare a negative constraint");
    }
    examples = immutableList(examples, "examples");
    String descriptorName = name;
    if (examples.stream().anyMatch(example -> !descriptorName.equals(example.toolName()))) {
      throw new IllegalArgumentException("examples must reference this tool");
    }
    requiredCapabilities = immutableSet(requiredCapabilities, "requiredCapabilities");
    requiredSuccessfulTools = immutableSet(requiredSuccessfulTools, "requiredSuccessfulTools");
    timeout = Objects.requireNonNull(timeout, "timeout");
    if (timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must not be negative");
    }
    resultSchema = Objects.requireNonNull(resultSchema, "resultSchema").deepCopy();
    AgentToolSchemaValidator.validateResultSchema(resultSchema);
  }

  public AgentToolDescriptor(
      String name,
      String label,
      String description,
      String category,
      ToolAccess access,
      ToolEffect effect,
      ToolResultMode resultMode,
      JsonObject parameters,
      List<String> whenToUse,
      List<String> whenNotToUse,
      List<ToolExample> examples,
      Set<String> requiredCapabilities,
      Set<String> requiredSuccessfulTools) {
    this(
        name,
        label,
        description,
        category,
        access,
        effect,
        resultMode,
        parameters,
        whenToUse,
        whenNotToUse,
        examples,
        requiredCapabilities,
        requiredSuccessfulTools,
        false,
        Duration.ZERO,
        anyResultSchema());
  }

  private static JsonObject anyResultSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "any");
    return schema;
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  private static <T> List<T> immutableList(List<T> values, String field) {
    return List.copyOf(Objects.requireNonNull(values, field));
  }

  private static <T> Set<T> immutableSet(Set<T> values, String field) {
    return Set.copyOf(Objects.requireNonNull(values, field));
  }
}
