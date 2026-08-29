package org.saturn.app.agent.api;

import java.util.Objects;

/** Describes an example invocation or response for an agent tool. */
public record ToolExample(String toolName, String arguments, String purpose) {
  public ToolExample {
    toolName = required(toolName, "toolName");
    arguments = Objects.requireNonNull(arguments, "arguments");
    purpose = required(purpose, "purpose");
  }

  /**
   * Implements the {@code required} operation for this agent component.
   *
   * @param value input argument used by this operation
   * @param field input argument used by this operation
   * @return the operation result
   */
  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
