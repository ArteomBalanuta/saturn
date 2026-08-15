package org.saturn.app.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

final class AgentToolSchemaValidator {
  private AgentToolSchemaValidator() {}

  static void validateSchema(JsonObject schema) {
    if (!schema.has("type")
        || !schema.get("type").isJsonPrimitive()
        || !"object".equals(schema.get("type").getAsString())) {
      throw new IllegalArgumentException("tool parameters must have an object root type");
    }
    if (schema.has("properties") && !schema.get("properties").isJsonObject()) {
      throw new IllegalArgumentException("tool properties must be an object");
    }
    if (schema.has("required")) {
      JsonElement required = schema.get("required");
      if (!required.isJsonArray()) {
        throw new IllegalArgumentException("tool required must be an array");
      }
      for (JsonElement name : required.getAsJsonArray()) {
        if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) {
          throw new IllegalArgumentException("tool required names must be strings");
        }
      }
    }
  }

  static String validateArguments(JsonObject schema, JsonObject arguments) {
    validateSchema(schema);
    JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
    if (schema.has("required")) {
      for (JsonElement required : schema.getAsJsonArray("required")) {
        if (!arguments.has(required.getAsString())) {
          return "Missing required parameter: " + required.getAsString();
        }
      }
    }
    if (schema.has("additionalProperties")
        && !schema.get("additionalProperties").getAsBoolean()) {
      for (String name : arguments.keySet()) {
        if (!properties.has(name)) {
          return "Unknown parameter: " + name;
        }
      }
    }
    for (var entry : arguments.entrySet()) {
      JsonObject property = properties.has(entry.getKey()) ? properties.getAsJsonObject(entry.getKey()) : null;
      if (property == null || !property.has("type")) {
        continue;
      }
      String expected = property.get("type").getAsString();
      JsonElement value = entry.getValue();
      boolean valid = switch (expected) {
        case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
        case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
        case "number", "integer" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
        case "object" -> value.isJsonObject();
        case "array" -> value.isJsonArray();
        case "null" -> value.isJsonNull();
        default -> true;
      };
      if (!valid) {
        return "Invalid type for parameter: " + entry.getKey();
      }
      String constraintError = validateConstraints(entry.getKey(), property, value, expected);
      if (constraintError != null) {
        return constraintError;
      }
    }
    return null;
  }

  private static String validateConstraints(
      String parameter, JsonObject schema, JsonElement value, String type) {
    if (schema.has("enum") && !contains(schema.getAsJsonArray("enum"), value)) {
      return "Invalid value for parameter: " + parameter;
    }
    if ("string".equals(type)) {
      int length = value.getAsString().codePointCount(0, value.getAsString().length());
      if (schema.has("minLength") && length < schema.get("minLength").getAsInt()) {
        return "Parameter is shorter than allowed: " + parameter;
      }
      if (schema.has("maxLength") && length > schema.get("maxLength").getAsInt()) {
        return "Parameter is longer than allowed: " + parameter;
      }
    }
    if ("number".equals(type) || "integer".equals(type)) {
      double number = value.getAsDouble();
      if ("integer".equals(type) && number != Math.rint(number)) {
        return "Invalid type for parameter: " + parameter;
      }
      if (schema.has("minimum") && number < schema.get("minimum").getAsDouble()) {
        return "Parameter is below minimum: " + parameter;
      }
      if (schema.has("maximum") && number > schema.get("maximum").getAsDouble()) {
        return "Parameter is above maximum: " + parameter;
      }
    }
    return null;
  }

  private static boolean contains(com.google.gson.JsonArray values, JsonElement candidate) {
    for (JsonElement value : values) {
      if (value.equals(candidate)) {
        return true;
      }
      if (value.isJsonPrimitive() && candidate.isJsonPrimitive()) {
        JsonPrimitive left = value.getAsJsonPrimitive();
        JsonPrimitive right = candidate.getAsJsonPrimitive();
        if (left.isNumber() && right.isNumber() && left.getAsDouble() == right.getAsDouble()) {
          return true;
        }
      }
    }
    return false;
  }
}
