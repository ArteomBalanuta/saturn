package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class FlairCommandImplTest {
  @Test
  void executeWithoutEnoughArgumentsReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*flair merc", "mod", "trip");

    var command = new FlairCommandImpl(engine, message, List.of("flair"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@mod \n Example: *flair merc trusted", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeFailsWhenTargetIsNotPresent() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*flair merc trusted", "mod", "trip");

    var command = new FlairCommandImpl(engine, message, List.of("flair"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals(
        "@mod User merc is not in the room, flair was not applied.",
        engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeAppliesFlairForActiveUser() {
    var engine = TestSupport.engine();
    engine.currentChannelUsers.add(TestSupport.user("merc", "trip-a", "hash-a"));
    var message = TestSupport.chatMessage("*flair @merc trusted", "mod", "trip");

    var command = new FlairCommandImpl(engine, message, List.of("flair"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals(
        "{ \"cmd\": \"forceflair\", \"nick\": \"merc\", \"flair\": \"trusted\" }",
        engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod \n Flair set successfully!", engine.outgoingMessageQueue.poll());
  }
}
