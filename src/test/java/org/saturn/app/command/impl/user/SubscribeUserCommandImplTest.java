package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;

class SubscribeUserCommandImplTest {

  @Test
  void executeSubscribesTripAndReturnsSuccess() {
    var engine = CommandTestSupport.engine();
    var message = CommandTestSupport.chatMessage("*sub", "testAuthor", "testTrip");

    var command = new SubscribeUserCommandImpl(engine, message, List.of("sub", "subscribe"));

    var result = command.execute();

    assertEquals(Status.SUCCESSFUL, result.orElseThrow());
    assertEquals(true, engine.subscribers.contains("testTrip"));
  }
}
