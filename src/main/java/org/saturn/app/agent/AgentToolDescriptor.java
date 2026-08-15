package org.saturn.app.agent;

import com.google.gson.JsonObject;
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
    Set<String> requiredSuccessfulTools) {
  public AgentToolDescriptor {
    name = required(name, "name");
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
    examples = immutableList(examples, "examples");
    String descriptorName = name;
    if (examples.stream().anyMatch(example -> !descriptorName.equals(example.toolName()))) {
      throw new IllegalArgumentException("examples must reference this tool");
    }
    requiredCapabilities = immutableSet(requiredCapabilities, "requiredCapabilities");
    requiredSuccessfulTools = immutableSet(requiredSuccessfulTools, "requiredSuccessfulTools");
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
