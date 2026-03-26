package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class MuteUserCommandImplTest {
  @Test
  void executeWithoutTargetReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*mute", "mod", "trip");

    var command = new MuteUserCommandImpl(engine, message, List.of("mute", "dumb"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@mod Example: *mute merc", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeFailsWhenTargetIsMissingFromRoom() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*mute merc", "mod", "trip");

    var command = new MuteUserCommandImpl(engine, message, List.of("mute", "dumb"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@mod merc is not in the room", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeMutesActiveUserAndIncludesHash() {
    var engine = TestSupport.engine();
    engine.currentChannelUsers.add(TestSupport.user("merc", "trip-a", "hash-a"));
    var message = TestSupport.chatMessage("*mute merc", "mod", "trip");

    var command = new MuteUserCommandImpl(engine, message, List.of("mute", "dumb"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals("{ \"cmd\": \"mute\", \"nick\": \"merc\"}", engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod merc hash-a has been muted", engine.outgoingMessageQueue.poll());
  }
}
