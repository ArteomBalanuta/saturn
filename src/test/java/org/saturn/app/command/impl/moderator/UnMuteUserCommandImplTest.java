package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class UnMuteUserCommandImplTest {
  @Test
  void executeWithoutHashReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*unmute", "mod", "trip");

    var command = new UnMuteUserCommandImpl(engine, message, List.of("unmute", "undumb"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@mod Example: *unmute jJ4M4fsECSazzlj", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeUnmutesHashAndQueuesConfirmation() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*unmute hash-a", "mod", "trip");

    var command = new UnMuteUserCommandImpl(engine, message, List.of("unmute", "undumb"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals(
        "{ \"cmd\": \"unmute\", \"hash\": \"hash-a\"}", engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod hash-a has been unmuted", engine.outgoingMessageQueue.poll());
  }
}
