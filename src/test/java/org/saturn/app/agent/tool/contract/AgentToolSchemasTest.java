package org.saturn.app.agent.tool.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class AgentToolSchemasTest {
  @Test
  void createsAnOpenObjectSchemaForLegacyTools() {
    JsonObject schema = AgentToolSchemas.object();

    assertTrue(schema.get("type").getAsString().equals("object"));
    assertTrue(schema.getAsJsonObject("properties").isEmpty());
    assertTrue(schema.get("additionalProperties").getAsBoolean());
  }

  @Test
  void createsAClosedObjectSchemaForStrictContracts() {
    JsonObject schema = AgentToolSchemas.closedObject();

    assertTrue(schema.get("type").getAsString().equals("object"));
    assertTrue(schema.getAsJsonObject("properties").isEmpty());
    assertFalse(schema.get("additionalProperties").getAsBoolean());
  }
}
