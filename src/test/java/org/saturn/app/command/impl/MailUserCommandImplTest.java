package org.saturn.app.command.impl;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.saturn.app.command.impl.user.MailUserCommandImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

class MailUserCommandImplTest {
  private final EngineImpl engine = mock(EngineImpl.class);
  private final ChatMessage message = mock(ChatMessage.class);

  @Test
  void getArgumentsTest() {
    doReturn("*mail merc\\ny\\no there message from merc").when(message).getText();
    doReturn("*").when(engine).getPrefix();
    MailUserCommandImpl cmd = new MailUserCommandImpl(engine, message, List.of());

    List<String> arguments = cmd.getArguments();

    Assertions.assertEquals(7, arguments.size());
  }
}
