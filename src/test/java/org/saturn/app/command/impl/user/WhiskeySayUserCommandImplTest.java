package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class WhiskeySayUserCommandImplTest {
  @Test
  void executeForwardsMessageToSupportReplica() {
    var engine = TestSupport.engine();
    var support = TestSupport.engine();
    support.channel = "support";
    engine.addReplica(support);
    var message = TestSupport.chatMessage("*ws test message", "testAuthor", "testTrip");

    var cmd = new WhiskeySayUserCommandImpl(engine, message, List.of("ws", "wsay"));

    assertEquals(Status.SUCCESSFUL, cmd.execute().orElseThrow());
    assertEquals(0, support.outgoingMessageQueue.size());
  }
}
