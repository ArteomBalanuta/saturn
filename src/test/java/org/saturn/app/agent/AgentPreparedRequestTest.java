package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;

class AgentPreparedRequestTest {
  @Test
  void copiesMessageAndDefinitionListsAndDeepCopiesMutableDefinitions() {
    List<LlmMessage> messages = new ArrayList<>(List.of(LlmMessage.user("prompt")));
    JsonObject definition = new JsonObject();
    definition.addProperty("name", "weather");
    List<JsonObject> definitions = new ArrayList<>(List.of(definition));

    AgentPreparedRequest request =
        new AgentPreparedRequest(messages, definitions, "prompt", null, Optional.of("alice"));

    messages.clear();
    definitions.clear();
    definition.addProperty("name", "changed");

    assertEquals(1, request.messages().size());
    assertEquals(1, request.definitions().size());
    assertEquals("weather", request.definitions().getFirst().get("name").getAsString());
    assertEquals(Optional.empty(), request.requiredFreshTool());
    assertEquals(Optional.of("alice"), request.requiredFreshNick());
    assertThrows(
        UnsupportedOperationException.class, () -> request.messages().add(LlmMessage.user("x")));
    assertThrows(UnsupportedOperationException.class, () -> request.definitions().clear());
  }
}
