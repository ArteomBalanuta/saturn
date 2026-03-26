package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class AfkUserCommandImplTest {
  @Test
  void executeMarksTripAsAfk() {
    var engine = TestSupport.engine();
    engine.currentChannelUsers.add(TestSupport.user("testAuthor", "testTrip", "hash-a"));
    var message = TestSupport.chatMessage("*afk test reason", "testAuthor", "testTrip");

    var cmd = new AfkUserCommandImpl(engine, message, List.of("afk", "a"));

    assertEquals(Status.SUCCESSFUL, cmd.execute().orElseThrow());
    assertTrue(engine.afkUsers.containsKey("testTrip"));
    assertEquals("@testAuthor  is afk", engine.outgoingMessageQueue.poll());
  }
}
