package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class LockRoomUserCommandImplTest {
  @Test
  void executeWithOnLocksRoom() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*lock on", "mod", "trip");

    var command = new LockRoomUserCommandImpl(engine, message, List.of("lock", "lockroom"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals("{ \"cmd\": \"lockroom\"}", engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod  Room locked!", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeWithOffUnlocksRoom() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*lock off", "mod", "trip");

    var command = new LockRoomUserCommandImpl(engine, message, List.of("lock", "lockroom"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals("{ \"cmd\": \"unlockroom\"}", engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod  Room unlocked!", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeWithoutArgumentsReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*lock", "mod", "trip");

    var command = new LockRoomUserCommandImpl(engine, message, List.of("lock", "lockroom"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@mod *lock [on|off]", engine.outgoingMessageQueue.poll());
  }
}
