package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentToolExecutionPolicyTest {
  private final AgentToolExecutionPolicy policy = new AgentToolExecutionPolicy();

  @Test
  void classifiesOnlyIndependentIdempotentReadsAsParallel() {
    assertEquals(
        AgentToolExecutionMode.PARALLEL_READ,
        policy.classify(descriptor(ToolEffect.READ_ONLY, true, Set.of())));
    assertEquals(
        AgentToolExecutionMode.SEQUENTIAL_DEPENDENT_READ,
        policy.classify(descriptor(ToolEffect.READ_ONLY, true, Set.of("database_schema"))));
    assertEquals(
        AgentToolExecutionMode.SEQUENTIAL_DEPENDENT_READ,
        policy.classify(descriptor(ToolEffect.READ_ONLY, false, Set.of())));
  }

  @Test
  void classifiesSideEffectingToolsAsSequentialActions() {
    for (ToolEffect effect :
        List.of(ToolEffect.ROOM_MESSAGE, ToolEffect.MODERATION, ToolEffect.PERSISTENCE)) {
      assertEquals(
          AgentToolExecutionMode.SEQUENTIAL_ACTION,
          policy.classify(descriptor(effect, true, Set.of())));
    }
  }

  private static AgentToolDescriptor descriptor(
      ToolEffect effect, boolean idempotent, Set<String> prerequisites) {
    JsonObject parameters = new JsonObject();
    parameters.addProperty("type", "object");
    parameters.add("properties", new JsonObject());
    JsonObject resultSchema = new JsonObject();
    resultSchema.addProperty("type", "any");
    return new AgentToolDescriptor(
        "test_tool",
        "Test tool",
        "Reads or modifies test data.",
        "test",
        ToolAccess.PUBLIC,
        effect,
        ToolResultMode.MODEL_DATA,
        parameters,
        List.of("Use for execution classification tests."),
        List.of("Do not use outside execution classification tests."),
        List.of(),
        Set.of(),
        prerequisites,
        idempotent,
        Duration.ZERO,
        resultSchema);
  }
}
