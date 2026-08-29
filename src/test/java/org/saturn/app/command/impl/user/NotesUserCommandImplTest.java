package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class NotesUserCommandImplTest {
  @Test
  void executeWithoutTripReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*notes", "testAuthor", null);

    var cmd = new NotesUserCommandImpl(engine, message, List.of("notes"));

    assertEquals(Status.FAILED, cmd.execute().orElseThrow());
    assertEquals(
        "@testAuthor \n Set your trip first. Example: *notes", engine.outgoingMessageQueue.poll());
  }
}
