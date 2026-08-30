package org.saturn.app.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.support.TestSupport;

class UserCommandBaseImplTest {
  @Test
  void parsesLeadingRepeatedAndTabWhitespaceWithoutIncludingCommandInArguments() {
    ExposedCommand command =
        new ExposedCommand(TestSupport.chatMessage("*   info\t  Alice   ", "author", "trip"));

    assertEquals(List.of("INFO"), command.getAliases());
    assertEquals(List.of("Alice"), command.getArguments());
  }

  private static final class ExposedCommand extends UserCommandBaseImpl {
    ExposedCommand(ChatMessage message) {
      super(message, TestSupport.engine(), List.of());
    }
  }
}
