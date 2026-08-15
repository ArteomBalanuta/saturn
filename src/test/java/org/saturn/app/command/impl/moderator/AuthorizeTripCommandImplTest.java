package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class AuthorizeTripCommandImplTest {
  @Test
  void executeWithoutTripReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*auth", "mod", "trip");

    var command = new AuthorizeTripCommandImpl(engine, message, List.of("authorize", "auth"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@mod  example: *auth cmdTV+", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeAuthorizesTrip() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*auth cmdTV+", "mod", "trip");

    var command = new AuthorizeTripCommandImpl(engine, message, List.of("authorize", "auth"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals(
        "{ \"cmd\": \"authtrip\", \"trip\": \"cmdTV+\"}", engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod  authorized trip: cmdTV+", engine.outgoingMessageQueue.poll());
  }
}
