package org.saturn.app.agent.tool.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.api.ToolAccess;
import org.saturn.app.agent.api.ToolEffect;
import org.saturn.app.agent.api.ToolResultMode;

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

  @Test
  void permitsAChangedRetryAfterFailureButEnforcesPerToolCallLimit() {
    AgentToolExecutionLedger ledger = new AgentToolExecutionLedger();

    assertEquals(
        AgentToolExecutionLedger.Reservation.ACCEPTED, ledger.reserve("read|{}", "read", 2));
    ledger.recordFailure("read|{}", "read", 3);
    assertEquals(
        AgentToolExecutionLedger.Reservation.ACCEPTED,
        ledger.reserve("read|{\"page\":2}", "read", 2));
    assertEquals(
        AgentToolExecutionLedger.Reservation.LIMIT_REACHED,
        ledger.reserve("read|{\"page\":3}", "read", 2));
  }

  @Test
  void returnsAnImmutablePrerequisiteSnapshot() {
    AgentToolExecutionLedger ledger = new AgentToolExecutionLedger();

    Set<String> missing = ledger.missingPrerequisites(descriptor(Set.of("read", "history")));

    assertEquals(Set.of("read", "history"), missing);
    assertThrows(UnsupportedOperationException.class, () -> missing.add("other"));
  }

  @Test
  void acceptsOnlyOneConcurrentReservationForAnInvocation() throws Exception {
    AgentToolExecutionLedger ledger = new AgentToolExecutionLedger();
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      List<Future<AgentToolExecutionLedger.Reservation>> results =
          executor.invokeAll(
              java.util.stream.IntStream.range(0, 8)
                  .mapToObj(
                      ignored ->
                          (java.util.concurrent.Callable<AgentToolExecutionLedger.Reservation>)
                              () -> ledger.reserve("read|{}", "read", 8))
                  .toList());

      long accepted = 0;
      long duplicates = 0;
      for (Future<AgentToolExecutionLedger.Reservation> result : results) {
        AgentToolExecutionLedger.Reservation reservation = result.get();
        if (reservation == AgentToolExecutionLedger.Reservation.ACCEPTED) {
          accepted++;
        } else if (reservation == AgentToolExecutionLedger.Reservation.DUPLICATE) {
          duplicates++;
        }
      }
      assertEquals(1, accepted);
      assertEquals(7, duplicates);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void enforcesPerToolLimitAcrossConcurrentDistinctInvocations() throws Exception {
    AgentToolExecutionLedger ledger = new AgentToolExecutionLedger();
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      List<Future<AgentToolExecutionLedger.Reservation>> results =
          executor.invokeAll(
              java.util.stream.IntStream.range(0, 8)
                  .mapToObj(
                      index ->
                          (java.util.concurrent.Callable<AgentToolExecutionLedger.Reservation>)
                              () -> ledger.reserve("read|{" + index + "}", "read", 2))
                  .toList());

      long accepted =
          results.stream()
              .map(Future::resultNow)
              .filter(AgentToolExecutionLedger.Reservation.ACCEPTED::equals)
              .count();
      long limited =
          results.stream()
              .map(Future::resultNow)
              .filter(AgentToolExecutionLedger.Reservation.LIMIT_REACHED::equals)
              .count();

      assertEquals(2, accepted);
      assertEquals(6, limited);
    } finally {
      executor.shutdownNow();
    }
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
