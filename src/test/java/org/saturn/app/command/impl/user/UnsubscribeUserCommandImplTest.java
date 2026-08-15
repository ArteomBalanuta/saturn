package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;

class UnsubscribeUserCommandImplTest {

  @Test
  void executeRemovesExistingSubscriptionAndReturnsSuccess() {
    var engine = CommandTestSupport.engine();
    engine.subscribers.add("testTrip");
    var message = CommandTestSupport.chatMessage("*unsub", "testAuthor", "testTrip");

    var command = new UnsubscribeUserCommandImpl(engine, message, List.of("unsub", "unsubscribe"));

    var result = command.execute();

    assertEquals(Status.SUCCESSFUL, result.orElseThrow());
    assertFalse(engine.subscribers.contains("testTrip"));
  }
}
