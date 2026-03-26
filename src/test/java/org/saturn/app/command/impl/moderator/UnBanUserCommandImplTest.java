package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class UnBanUserCommandImplTest {
  @Test
  void executeWithoutHashReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*unban", "mod", "trip");

    var command = new UnBanUserCommandImpl(engine, message, List.of("unban"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@mod Example: *unban HjkUEWNlIRH35Xk", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeUnbansHashAndQueuesConfirmation() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*unban HjkUEWNlIRH35Xk", "mod", "trip");

    var command = new UnBanUserCommandImpl(engine, message, List.of("unban"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals(
        "{ \"cmd\": \"unban\", \"hash\": \"HjkUEWNlIRH35Xk\"}",
        engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod HjkUEWNlIRH35Xk has been unbanned", engine.outgoingMessageQueue.poll());
  }
}
