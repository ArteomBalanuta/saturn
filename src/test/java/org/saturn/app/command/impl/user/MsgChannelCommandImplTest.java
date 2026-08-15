package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

class MsgChannelCommandImplTest {
  @Test
  void executeCurrentRoomMessageQueuesFormattedMessage() {
    EngineImpl engine = CommandTestSupport.engine();
    engine.channel = "programming";
    ChatMessage message =
        CommandTestSupport.chatMessage(
            "*msgchannel programming test message", "testAuthor", "testTrip");

    MsgChannelCommandImpl cmd = new MsgChannelCommandImpl(engine, message, List.of());

    Status result = cmd.execute().orElseThrow();

    assertEquals(Status.SUCCESSFUL, result);
    assertEquals(
        "@testAuthor  anonymous mail from: ?programming message: test message",
        engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeFailsWhenMessageBodyMissing() {
    EngineImpl engine = CommandTestSupport.engine();
    ChatMessage message =
        CommandTestSupport.chatMessage("*msgchannel programming", "testAuthor", "testTrip");

    MsgChannelCommandImpl cmd = new MsgChannelCommandImpl(engine, message, List.of());

    Status result = cmd.execute().orElseThrow();

    assertEquals(Status.FAILED, result);
    assertEquals(
        "@testAuthor  Example: *msgroom your-room your message",
        engine.outgoingMessageQueue.poll());
  }
}
