package org.saturn.app.listener.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.saturn.app.facade.EngineType;

class DefaultRoomSnapshotCoordinatorTest {
  @Test
  void runsFirstSnapshotOnceAndAlwaysCloses() {
    FakeSession session = new FakeSession();
    AtomicInteger applied = new AtomicInteger();
    List<String> replies = new ArrayList<>();
    DefaultRoomSnapshotCoordinator coordinator =
        new DefaultRoomSnapshotCoordinator(
            (request, sink) -> session,
            (request, reply) -> replies.add(reply),
            new GsonOnlineSetPayloadParser(EngineType.LIST_CMD, "wf", "room"));

    coordinator.submit(request(applied));
    coordinator.onSnapshot("session", "{\"cmd\":\"onlineSet\",\"users\":[]}");
    coordinator.onSnapshot("session", "{\"cmd\":\"onlineSet\",\"users\":[]}");

    assertEquals(1, applied.get());
    assertEquals(1, session.closed);
    assertEquals(1, session.flushed);
    assertEquals(0, coordinator.activeWorkflowCount());
  }

  @Test
  void startFailureStillClosesAndRemovesWorkflow() {
    FakeSession session = new FakeSession();
    session.failStart = true;
    DefaultRoomSnapshotCoordinator coordinator =
        new DefaultRoomSnapshotCoordinator(
            (request, sink) -> session,
            (request, reply) -> {},
            new GsonOnlineSetPayloadParser(EngineType.LIST_CMD, "wf", "room"));

    coordinator.submit(request(new AtomicInteger()));

    assertEquals(1, session.closed);
    assertEquals(0, coordinator.activeWorkflowCount());
  }

  private static RoomSnapshotRequest request(AtomicInteger applied) {
    return new RoomSnapshotRequest(
        "wf",
        "author",
        "source",
        "room",
        null,
        null,
        (context, snapshot) -> {
          applied.incrementAndGet();
          return OperationResult.success();
        });
  }

  private static final class FakeSession implements DefaultRoomSnapshotCoordinator.Session {
    int closed;
    int flushed;
    boolean failStart;

    public String id() {
      return "session";
    }

    public void start() throws Exception {
      if (failStart) throw new Exception("start");
    }

    public void close() {
      closed++;
    }

    public void flush() {
      flushed++;
    }

    public void sendRaw(String payload) {}
  }
}
