package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class PingUserCommandImplTest {
  @Test
  void executeQueuesLatencyMessage() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*ping", "testAuthor", "testTrip");

    var command = new PingUserCommandImpl(engine, message, List.of("ping", "p"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    String payload = engine.outgoingMessageQueue.poll();
    assertNotNull(payload);
    assertTrue(payload.startsWith("@testAuthor response time: "));
    assertTrue(payload.endsWith(" milliseconds"));
  }
}
