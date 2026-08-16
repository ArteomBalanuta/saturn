package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentToolDefinitionJsonTest {
  @Test
  void extractsFunctionNameOnlyFromFunctionDefinitions() {
    JsonObject definition = new JsonObject();
    JsonObject function = new JsonObject();
    function.addProperty("name", "run_command");
    definition.add("function", function);

    assertEquals(Optional.of("run_command"), AgentToolDefinitionJson.functionName(definition));
    assertTrue(AgentToolDefinitionJson.functionName(new JsonObject()).isEmpty());
  }

  @Test
  void rejectsMalformedFunctionContainersAndNonStringNames() {
    JsonObject primitiveFunction = new JsonObject();
    primitiveFunction.addProperty("function", "run_command");
    JsonObject arrayFunction = new JsonObject();
    arrayFunction.add("function", new com.google.gson.JsonArray());
    JsonObject numericName = new JsonObject();
    JsonObject numericFunction = new JsonObject();
    numericFunction.addProperty("name", 42);
    numericName.add("function", numericFunction);
    JsonObject nullName = new JsonObject();
    JsonObject nullFunction = new JsonObject();
    nullFunction.add("name", com.google.gson.JsonNull.INSTANCE);
    nullName.add("function", nullFunction);

    assertTrue(AgentToolDefinitionJson.functionName(null).isEmpty());
    assertTrue(AgentToolDefinitionJson.functionName(primitiveFunction).isEmpty());
    assertTrue(AgentToolDefinitionJson.functionName(arrayFunction).isEmpty());
    assertTrue(AgentToolDefinitionJson.functionName(numericName).isEmpty());
    assertTrue(AgentToolDefinitionJson.functionName(nullName).isEmpty());
  }

  @Test
  void preservesTheProviderSuppliedFunctionNameExactly() {
    JsonObject definition = new JsonObject();
    JsonObject function = new JsonObject();
    function.addProperty("name", " room_users ");
    definition.add("function", function);

    assertEquals(Optional.of(" room_users "), AgentToolDefinitionJson.functionName(definition));
  }
}
