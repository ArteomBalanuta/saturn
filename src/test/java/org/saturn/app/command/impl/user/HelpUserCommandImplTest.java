package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class HelpUserCommandImplTest {
  @Test
  void executeKeepsThinSpaceFormattedPayload() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*help", "testAuthor", "trip");

    var command = new HelpUserCommandImpl(engine, message, List.of("help", "h"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());

    String payload = engine.outgoingMessageQueue.poll();
    assertTrue(
        payload.startsWith(
            "@testAuthor All commands can be used through '/whisper'\nPrefix: * \nCommands:\n"));
    assertTrue(
        payload.contains("\u2009\u2009\u2009\u2009\u2009\u2009\u2009\u2009 \n Admin commands:\n"));
    assertTrue(payload.contains("\u2009prefix <char>"));
    assertTrue(payload.contains("\u2009msgroom <room> <text>\u2009"));
    assertTrue(payload.contains("\u2009 %scaptcha on ".formatted("*")));
  }
}
