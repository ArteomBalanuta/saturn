package org.saturn.app.agent;

import java.util.Objects;

public record ToolExample(String toolName, String arguments, String purpose) {
  public ToolExample {
    toolName = required(toolName, "toolName");
    arguments = Objects.requireNonNull(arguments, "arguments");
    purpose = required(purpose, "purpose");
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
