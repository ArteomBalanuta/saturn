package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class DeAuthorizeTripCommandImplTest {
  @Test
  void executeWithoutTripReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*deauth", "mod", "trip");

    var command = new DeAuthorizeTripCommandImpl(engine, message, List.of("deauthorize", "deauth"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@mod  example: *deauth cmdTV+", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeDeauthorizesTrip() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*deauth cmdTV+", "mod", "trip");

    var command = new DeAuthorizeTripCommandImpl(engine, message, List.of("deauthorize", "deauth"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals(
        "{ \"cmd\": \"deauthtrip\", \"trip\": \"cmdTV+\"}", engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod  deauthorized trip: cmdTV+", engine.outgoingMessageQueue.poll());
  }
}
