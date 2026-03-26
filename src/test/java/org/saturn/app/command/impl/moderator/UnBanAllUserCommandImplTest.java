package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class UnBanAllUserCommandImplTest {
  @Test
  void executeSendsUnbanAllAndMercyMessage() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*unbanall", "mod", "trip");

    var command = new UnBanAllUserCommandImpl(engine, message, List.of("unbanall", "pardonall"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals("{ \"cmd\": \"unbanall\"}", engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod mercy.", engine.outgoingMessageQueue.poll());
  }
}
