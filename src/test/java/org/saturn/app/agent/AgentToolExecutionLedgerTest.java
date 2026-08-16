package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentToolExecutionLedgerTest {
  @Test
  void reservesOnceAndUnlocksPrerequisitesAfterSuccess() {
    AgentToolExecutionLedger ledger = new AgentToolExecutionLedger();
    assertEquals(
        AgentToolExecutionLedger.Reservation.ACCEPTED, ledger.reserve("read|{}", "read", 2));
    assertEquals(
        AgentToolExecutionLedger.Reservation.DUPLICATE, ledger.reserve("read|{}", "read", 2));
    assertEquals(Set.of("read"), ledger.missingPrerequisites(descriptor(Set.of("read"))));

    ledger.recordSuccess("read|{}", "read");

    assertTrue(ledger.missingPrerequisites(descriptor(Set.of("read"))).isEmpty());
  }

  @Test
  void disablesToolAtFailureThreshold() {
    AgentToolExecutionLedger ledger = new AgentToolExecutionLedger();
    assertFalse(ledger.isDisabled("read"));
    ledger.recordValidationFailure("read", 2);
    ledger.recordValidationFailure("read", 2);
    assertTrue(ledger.isDisabled("read"));
  }

  private static AgentToolDescriptor descriptor(Set<String> prerequisites) {
    JsonObject parameters = new JsonObject();
    parameters.addProperty("type", "object");
    parameters.add("properties", new JsonObject());
    return new AgentToolDescriptor(
        "dependent",
        "Dependent",
        "Reads data after another tool succeeds.",
        "test",
        ToolAccess.PUBLIC,
        ToolEffect.READ_ONLY,
        ToolResultMode.MODEL_DATA,
        parameters,
        List.of("Use after prerequisite data is available."),
        List.of("Do not use before its prerequisite succeeds."),
        List.of(),
        Set.of(),
        prerequisites);
  }
}
