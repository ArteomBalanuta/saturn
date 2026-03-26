package org.saturn.app.command.impl.user;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.command.impl.user.NotesUserCommandImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

class NotesUserCommandImplTest {
  private final EngineImpl engine = mock(EngineImpl.class);
  private final ChatMessage message = mock(ChatMessage.class);

  @Test
  void executeTest() {
    // Basic test to verify command instantiation and execution doesn't throw exception
    doReturn("*notes").when(message).getText();
    doReturn("*").when(engine).getPrefix();
    doReturn("testAuthor").when(message).getNick();
    doReturn("testTrip").when(message).getTrip();
    
    NotesUserCommandImpl cmd = new NotesUserCommandImpl(engine, message, List.of());

    // Execute - Should not throw exception
    Optional<Status> result = cmd.execute();

    // Verify - At least we can verify execution doesn't crash
    assertNotNull(result);
  }
}