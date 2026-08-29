package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;

class MailUserCommandImplTest {

  @Test
  void executeWithoutArgumentsReturnsFailure() {
    var engine = CommandTestSupport.engine();
    var message = CommandTestSupport.chatMessage("*mail", "testAuthor", "testTrip");

    var command = new MailUserCommandImpl(engine, message, List.of("mail", "msg", "send"));

    var result = command.execute();

    assertEquals(Status.FAILED, result.orElseThrow());
  }

  @Test
  void getArgumentsSplitsEscapedReceiverLines() {
    var engine = CommandTestSupport.engine();
    var message =
        CommandTestSupport.chatMessage(
            "*mail merc\\ny\\no there message from merc", "tester", "trip");

    var command = new MailUserCommandImpl(engine, message, List.of("mail", "msg", "send"));

    assertEquals(7, command.getArguments().size());
  }
}
