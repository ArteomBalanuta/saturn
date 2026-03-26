package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class BanUserCommandImplTest {
  @Test
  void executeWithoutTargetReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*ban", "mod", "trip");

    var command = new BanUserCommandImpl(engine, message, List.of("ban"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@mod Example: *ban merc", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeBansTargetAndQueuesConfirmation() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*ban merc", "mod", "trip");

    var command = new BanUserCommandImpl(engine, message, List.of("ban"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals("{ \"cmd\": \"ban\", \"nick\": \"merc\"}", engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod merc has been banned", engine.outgoingMessageQueue.poll());
  }
}
