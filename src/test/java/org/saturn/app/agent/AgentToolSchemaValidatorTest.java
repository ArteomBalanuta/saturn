package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class AgentToolSchemaValidatorTest {
  @Test
  void rejectsMalformedParameterSchemaDeclarations() {
    JsonObject missingType = new JsonObject();
    assertThrows(
        IllegalArgumentException.class, () -> AgentToolSchemaValidator.validateSchema(missingType));

    JsonObject nonObjectProperties = objectSchema();
    nonObjectProperties.addProperty("properties", "invalid");
    assertThrows(
        IllegalArgumentException.class,
        () -> AgentToolSchemaValidator.validateSchema(nonObjectProperties));

    JsonObject nonBooleanAdditionalProperties = objectSchema();
    nonBooleanAdditionalProperties.addProperty("additionalProperties", "false");
    assertThrows(
        IllegalArgumentException.class,
        () -> AgentToolSchemaValidator.validateSchema(nonBooleanAdditionalProperties));
  }

  @Test
  void rejectsMalformedRequiredDeclarations() {
    JsonObject nonArray = objectSchema();
    nonArray.addProperty("required", "value");
    assertThrows(
        IllegalArgumentException.class, () -> AgentToolSchemaValidator.validateSchema(nonArray));

    JsonObject nonStringName = objectSchema();
    JsonArray required = new JsonArray();
    required.add(1);
    nonStringName.add("required", required);
    assertThrows(
        IllegalArgumentException.class,
        () -> AgentToolSchemaValidator.validateSchema(nonStringName));
  }

  @Test
  void validatesRequiredArgumentsAndClosedObjectArguments() {
    JsonObject schema = objectSchema();
    schema.add("properties", propertyMap(stringProperty("name")));
    JsonArray required = new JsonArray();
    required.add("name");
    schema.add("required", required);

    JsonObject missing = new JsonObject();
    assertEquals(
        "Missing required parameter: name",
        AgentToolSchemaValidator.validateArguments(schema, missing));

    JsonObject unknown = new JsonObject();
    unknown.addProperty("name", "present");
    unknown.addProperty("extra", true);
    assertEquals(
        "Unknown parameter: extra", AgentToolSchemaValidator.validateArguments(schema, unknown));
  }

  @Test
  void validatesPrimitiveTypesAndStructuredTypes() {
    JsonObject schema = objectSchema();
    JsonObject properties = new JsonObject();
    properties.add("text", stringProperty("text"));
    properties.add("flag", typedProperty("boolean"));
    properties.add("number", typedProperty("number"));
    properties.add("integer", typedProperty("integer"));
    properties.add("object", typedProperty("object"));
    properties.add("array", typedProperty("array"));
    properties.add("nothing", typedProperty("null"));
    schema.add("properties", properties);

    JsonObject valid = new JsonObject();
    valid.addProperty("text", "ok");
    valid.addProperty("flag", true);
    valid.addProperty("number", 1.5);
    valid.addProperty("integer", 2);
    valid.add("object", new JsonObject());
    valid.add("array", new JsonArray());
    valid.add("nothing", JsonNull.INSTANCE);
    assertEquals(null, AgentToolSchemaValidator.validateArguments(schema, valid));

    valid.addProperty("flag", "not boolean");
    assertEquals(
        "Invalid type for parameter: flag",
        AgentToolSchemaValidator.validateArguments(schema, valid));
  }

  @Test
  void validatesEnumStringLengthAndNumericBounds() {
    JsonObject schema = objectSchema();
    JsonObject properties = new JsonObject();
    JsonObject text = stringProperty("text");
    text.addProperty("minLength", 2);
    text.addProperty("maxLength", 4);
    JsonArray values = new JsonArray();
    values.add("no");
    values.add("x");
    values.add("longer");
    text.add("enum", values);
    properties.add("text", text);
    JsonObject integer = typedProperty("integer");
    integer.addProperty("minimum", 1);
    integer.addProperty("maximum", 3);
    properties.add("integer", integer);
    schema.add("properties", properties);

    JsonObject arguments = new JsonObject();
    arguments.addProperty("text", "no");
    arguments.addProperty("integer", 2);
    assertEquals(null, AgentToolSchemaValidator.validateArguments(schema, arguments));

    arguments.addProperty("text", "bad");
    assertEquals(
        "Invalid value for parameter: text",
        AgentToolSchemaValidator.validateArguments(schema, arguments));
    arguments.addProperty("text", "x");
    assertEquals(
        "Parameter is shorter than allowed: text",
        AgentToolSchemaValidator.validateArguments(schema, arguments));
    arguments.addProperty("text", "longer");
    assertEquals(
        "Parameter is longer than allowed: text",
        AgentToolSchemaValidator.validateArguments(schema, arguments));
    arguments.addProperty("text", "no");
    arguments.addProperty("integer", 1.5);
    assertEquals(
        "Invalid type for parameter: integer",
        AgentToolSchemaValidator.validateArguments(schema, arguments));
    arguments.addProperty("integer", 0);
    assertEquals(
        "Parameter is below minimum: integer",
        AgentToolSchemaValidator.validateArguments(schema, arguments));
    arguments.addProperty("integer", 4);
    assertEquals(
        "Parameter is above maximum: integer",
        AgentToolSchemaValidator.validateArguments(schema, arguments));
  }

  @Test
  void validatesResultTypeAndRequiredFields() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", propertyMapWithName("answer", stringProperty("answer")));
    JsonArray required = new JsonArray();
    required.add("answer");
    schema.add("required", required);

    JsonObject missing = new JsonObject();
    assertEquals(
        "Tool result is missing required field: answer",
        AgentToolSchemaValidator.validateResult(schema, missing));

    JsonObject valid = new JsonObject();
    valid.addProperty("answer", "done");
    assertEquals(null, AgentToolSchemaValidator.validateResult(schema, valid));
    assertEquals(
        "Tool result does not match declared object schema",
        AgentToolSchemaValidator.validateResult(schema, new JsonArray()));
  }

  @Test
  void acceptsSupportedResultTypesAndRejectsMalformedResultSchemas() {
    for (String type :
        new String[] {"any", "string", "boolean", "number", "integer", "object", "array", "null"}) {
      JsonObject schema = new JsonObject();
      schema.addProperty("type", type);
      assertDoesNotThrow(() -> AgentToolSchemaValidator.validateResultSchema(schema));
    }

    JsonObject missingType = new JsonObject();
    assertThrows(
        IllegalArgumentException.class,
        () -> AgentToolSchemaValidator.validateResultSchema(missingType));
    JsonObject nonStringType = new JsonObject();
    nonStringType.addProperty("type", 1);
    assertThrows(
        IllegalArgumentException.class,
        () -> AgentToolSchemaValidator.validateResultSchema(nonStringType));
  }

  @Test
  void rejectsMalformedResultRequiredDeclarations() {
    JsonObject nonArray = new JsonObject();
    nonArray.addProperty("type", "object");
    nonArray.addProperty("required", "answer");
    assertThrows(
        IllegalArgumentException.class,
        () -> AgentToolSchemaValidator.validateResultSchema(nonArray));

    JsonObject undeclared = new JsonObject();
    undeclared.addProperty("type", "object");
    JsonArray required = new JsonArray();
    required.add("answer");
    undeclared.add("required", required);
    assertThrows(
        IllegalArgumentException.class,
        () -> AgentToolSchemaValidator.validateResultSchema(undeclared));
  }

  private static JsonObject objectSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.addProperty("additionalProperties", false);
    return schema;
  }

  private static JsonObject propertyMap(JsonObject property) {
    return propertyMapWithName("name", property);
  }

  private static JsonObject propertyMapWithName(String name, JsonObject property) {
    JsonObject properties = new JsonObject();
    properties.add(name, property);
    return properties;
  }

  private static JsonObject stringProperty(String description) {
    return typedProperty("string");
  }

  private static JsonObject typedProperty(String type) {
    JsonObject property = new JsonObject();
    property.addProperty("type", type);
    return property;
  }
}
