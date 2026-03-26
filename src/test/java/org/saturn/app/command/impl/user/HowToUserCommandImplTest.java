package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class HowToUserCommandImplTest {
  @Test
  void executeQueuesModerationGuide() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*howto", "testAuthor", "testTrip");

    var cmd = new HowToUserCommandImpl(engine, message, List.of("howto", "hcguide"));

    assertEquals(Status.SUCCESSFUL, cmd.execute().orElseThrow());
    String payload = engine.outgoingMessageQueue.poll();
    assertTrue(payload.startsWith("@testAuthor hack.chat moderation guide"));
    assertTrue(payload.contains("youtu.be/E_Yl9ul3Ulw"));
  }
}
