package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;

class NoteUserCommandImplTest {

  @Test
  void executeWithoutArgumentsReturnsFailure() {
    var engine = CommandTestSupport.engine();
    var message = CommandTestSupport.chatMessage("*note", "testAuthor", "testTrip");

    var command = new NoteUserCommandImpl(engine, message, List.of("note", "save"));

    var result = command.execute();

    assertEquals(Status.FAILED, result.orElseThrow());
  }
}
