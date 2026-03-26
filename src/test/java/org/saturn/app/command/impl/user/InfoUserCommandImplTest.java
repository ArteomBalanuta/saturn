package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class InfoUserCommandImplTest {
  @Test
  void executeReturnsUserTripAndHash() {
    var engine = TestSupport.engine();
    engine.currentChannelUsers.add(TestSupport.user("merc", "trip-a", "hash-a"));
    var message = TestSupport.chatMessage("*info merc", "testAuthor", "testTrip");

    var cmd = new InfoUserCommandImpl(engine, message, List.of("info", "whois"));

    assertEquals(Status.SUCCESSFUL, cmd.execute().orElseThrow());
    assertEquals(
        "@testAuthor \\n User trip: trip-a\\n User hash: hash-a",
        engine.outgoingMessageQueue.poll());
  }
}
