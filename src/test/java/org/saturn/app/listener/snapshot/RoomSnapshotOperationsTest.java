package org.saturn.app.listener.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.dto.User;

class RoomSnapshotOperationsTest {
  private static RoomSnapshotContext context(List<String> raw, List<String> replies) {
    return new RoomSnapshotContext(
        "wf", "author", "source", "room", "destination", replies::add, raw::add);
  }

  @Test
  void messageOperationSendsOnlyWhenAnotherUserIsPresent() {
    List<String> raw = new ArrayList<>();
    List<String> replies = new ArrayList<>();
    OnlineSetSnapshot snapshot =
        new OnlineSetSnapshot(List.of(new User("bot"), new User("Alice")), false);
    OperationResult result =
        new DeliverMessageToRoomOperation("hello").apply(context(raw, replies), snapshot);
    assertEquals(OperationOutcome.SUCCESS, result.outcome());
    assertEquals("sent successfully.", result.reply());
    assertEquals(1, raw.size());
  }

  @Test
  void kickMatchingNormalizesAtAndCase() {
    List<String> raw = new ArrayList<>();
    OperationResult result =
        new KickOrResurrectOperation(" @ALICE ")
            .apply(
                context(raw, new ArrayList<>()),
                new OnlineSetSnapshot(List.of(new User("alice")), false));
    assertEquals(OperationOutcome.SUCCESS, result.outcome());
    assertTrue(raw.getFirst().contains("ALICE"));
  }

  @Test
  void nukeContinuesAfterIndividualSendFailureAndAttemptsLock() {
    List<String> raw = new ArrayList<>();
    RoomSnapshotContext context = context(raw, new ArrayList<>());
    RoomSnapshotContext failing =
        new RoomSnapshotContext(
            "wf",
            "author",
            "source",
            "room",
            "destination",
            context.reply(),
            payload -> {
              if (payload.contains("Alice")) throw new RuntimeException("send");
              raw.add(payload);
            });
    OperationResult result =
        new NukeRoomOperation(0)
            .apply(
                failing, new OnlineSetSnapshot(List.of(new User("Alice"), new User("Bob")), false));
    assertEquals(OperationOutcome.FAILED, result.outcome());
    assertEquals(2, raw.size());
    assertTrue(raw.getLast().contains("lockroom"));
  }
}
