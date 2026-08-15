package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class VersionUserCommandImplTest {
  @Test
  void executeQueuesCurrentVersionForAuthor() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*version", "testAuthor", "testTrip");

    var command = new VersionUserCommandImpl(engine, message, List.of("version", "v"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    String payload = engine.outgoingMessageQueue.poll();
    assertNotNull(payload);
    assertEquals("@testAuthor 1.0.29", payload);
  }
}
