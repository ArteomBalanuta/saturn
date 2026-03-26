package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class SayUserCommandImplTest {
  @Test
  void executeEnqueuesRenderedMessage() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*say hello world", "testAuthor", "testTrip");

    var cmd = new SayUserCommandImpl(engine, message, List.of("say", "echo"));

    assertEquals(Status.SUCCESSFUL, cmd.execute().orElseThrow());
    assertEquals("hello world ", engine.outgoingMessageQueue.poll());
  }
}
