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
    validatePropertyTypes(schema, "tool parameter");
    if (schema.has("additionalProperties")
        && (!schema.get("additionalProperties").isJsonPrimitive()
            || !schema.get("additionalProperties").getAsJsonPrimitive().isBoolean())) {
      throw new IllegalArgumentException("tool additionalProperties must be a boolean");
    }
    if (schema.has("required")) {
      JsonElement required = schema.get("required");
      if (!required.isJsonArray()) {
        throw new IllegalArgumentException("tool required must be an array");
      }
      JsonObject properties =
          schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
      for (JsonElement name : required.getAsJsonArray()) {
        if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) {
          throw new IllegalArgumentException("tool required names must be strings");
        }
        if (!properties.has(name.getAsString())) {
          throw new IllegalArgumentException("tool required names must be declared properties");
        }
      }
    }
  }

  static void validateResultSchema(JsonObject schema) {
    if (!schema.has("type")
        || !schema.get("type").isJsonPrimitive()
        || !schema.get("type").getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("tool result schema must declare a type");
    }
    if (schema.has("properties") && !schema.get("properties").isJsonObject()) {
      throw new IllegalArgumentException("tool result properties must be an object");
    }
    if (!isSupportedType(schema.get("type").getAsString())) {
      throw new IllegalArgumentException("tool result schema has an unsupported type");
    }
    validatePropertyTypes(schema, "tool result");
  }

  static String validateArguments(JsonObject schema, JsonObject arguments) {
    validateSchema(schema);
    JsonObject properties =
        schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
    if (schema.has("required")) {
      for (JsonElement required : schema.getAsJsonArray("required")) {
        if (!arguments.has(required.getAsString())) {
          return "Missing required parameter: " + required.getAsString();
        }
      }
    }
    if (schema.has("additionalProperties") && !schema.get("additionalProperties").getAsBoolean()) {
      for (String name : arguments.keySet()) {
        if (!properties.has(name)) {
          return "Unknown parameter: " + name;
        }
      }
    }
    for (var entry : arguments.entrySet()) {
      JsonObject property =
          properties.has(entry.getKey()) ? properties.getAsJsonObject(entry.getKey()) : null;
      if (property == null || !property.has("type")) {
        continue;
      }
      String expected = property.get("type").getAsString();
      JsonElement value = entry.getValue();
      boolean valid =
          switch (expected) {
            case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "number", "integer" ->
                value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
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

  static String validateResult(JsonObject schema, JsonElement result) {
    validateResultSchema(schema);
    String type = schema.get("type").getAsString();
    if (!matchesType(type, result)) {
      return "Tool result does not match declared " + type + " schema";
    }
    if ("object".equals(type) && schema.has("required")) {
      JsonObject object = result.getAsJsonObject();
      for (JsonElement required : schema.getAsJsonArray("required")) {
        if (!object.has(required.getAsString())) {
          return "Tool result is missing required field: " + required.getAsString();
        }
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

  private static boolean matchesType(String expected, JsonElement value) {
    return switch (expected) {
      case "any" -> true;
      case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
      case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
      case "number", "integer" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
      case "object" -> value.isJsonObject();
      case "array" -> value.isJsonArray();
      case "null" -> value.isJsonNull();
      default -> false;
    };
  }

  private static void validatePropertyTypes(JsonObject schema, String subject) {
    if (!schema.has("properties")) {
      return;
    }
    for (var property : schema.getAsJsonObject("properties").entrySet()) {
      if (!property.getValue().isJsonObject()) {
        throw new IllegalArgumentException(
            subject + " property must be an object: " + property.getKey());
      }
      JsonObject definition = property.getValue().getAsJsonObject();
      if (!definition.has("type")
          || !definition.get("type").isJsonPrimitive()
          || !definition.get("type").getAsJsonPrimitive().isString()
          || !isSupportedType(definition.get("type").getAsString())) {
        throw new IllegalArgumentException(
            subject + " property has an unsupported type: " + property.getKey());
      }
    }
  }

  private static boolean isSupportedType(String type) {
    return switch (type) {
      case "any", "string", "boolean", "number", "integer", "object", "array", "null" -> true;
      default -> false;
    };
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
