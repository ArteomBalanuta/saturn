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
}
