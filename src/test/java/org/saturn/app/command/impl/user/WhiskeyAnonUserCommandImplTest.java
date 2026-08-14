package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class WhiskeyAnonUserCommandImplTest {
  @Test
  void executeForwardsAnonymousMessageToSupportReplica() {
    var engine = TestSupport.engine();
    var support = TestSupport.engine();
    support.channel = "support";
    engine.addReplica(support);
    var message = TestSupport.chatMessage("*wsa test message", "testAuthor", "testTrip");

    var cmd = new WhiskeyAnonUserCommandImpl(engine, message, List.of("wsa", "anonsay"));

    assertEquals(Status.SUCCESSFUL, cmd.execute().orElseThrow());
    assertEquals(0, support.outgoingMessageQueue.size());
  }
}
