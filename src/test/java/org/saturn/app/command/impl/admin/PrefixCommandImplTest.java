package org.saturn.app.command.impl.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class PrefixCommandImplTest {
  @Test
  void executeWithoutArgumentReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*prefix", "admin", "trip");

    var command = new PrefixCommandImpl(engine, message, List.of("prefix"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@admin Example: *prefix $", engine.outgoingMessageQueue.poll());
    assertEquals("*", engine.prefix);
  }

  @Test
  void executeChangesPrefixForHostAndReplicas() {
    var engine = TestSupport.engine();
    var replica = TestSupport.engine();
    replica.channel = "lounge";
    engine.addReplica(replica);
    var message = TestSupport.chatMessage("*prefix $", "admin", "trip");

    var command = new PrefixCommandImpl(engine, message, List.of("prefix"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals("$", engine.prefix);
    assertEquals("$", replica.prefix);
    assertEquals("@admin prefix changed from * to $", engine.outgoingMessageQueue.poll());
  }
}
