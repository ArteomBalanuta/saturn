package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class ColorCommandImplTest {
  @Test
  void executeWithoutEnoughArgumentsReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*color merc", "mod", "trip");

    var command = new ColorCommandImpl(engine, message, List.of("color"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@mod \\n Example: *color merc 00ff00", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeFailsWhenTargetIsNotPresent() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*color merc 00ff00", "mod", "trip");

    var command = new ColorCommandImpl(engine, message, List.of("color"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals(
        "@mod User merc is not in the room, color was not applied.",
        engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeAppliesColorForActiveUser() {
    var engine = TestSupport.engine();
    engine.currentChannelUsers.add(TestSupport.user("merc", "trip-a", "hash-a"));
    var message = TestSupport.chatMessage("*color @merc 00ff00", "mod", "trip");

    var command = new ColorCommandImpl(engine, message, List.of("color"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals(
        "{ \"cmd\": \"forcecolor\", \"nick\": \"merc\", \"color\": \"00ff00\" }",
        engine.outgoingRawMessageQueue.poll());
  }
}
