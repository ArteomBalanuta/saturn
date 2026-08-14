package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class ApeUserCommandImplTest {
  @Test
  void executeQueuesAsciiApe() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*ape", "testAuthor", "testTrip");

    var cmd = new ApeUserCommandImpl(engine, message, List.of("ape", "harambe"));

    assertEquals(Status.SUCCESSFUL, cmd.execute().orElseThrow());
    String payload = engine.outgoingMessageQueue.poll();
    assertTrue(payload.startsWith("@testAuthor "));
    assertTrue(payload.contains("\n"));
  }
}
