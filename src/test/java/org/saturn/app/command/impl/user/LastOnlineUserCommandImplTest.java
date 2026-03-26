package org.saturn.app.command.impl.user;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.command.impl.user.LastOnlineUserCommandImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

class LastOnlineUserCommandImplTest {
  private final EngineImpl engine = mock(EngineImpl.class);
  private final ChatMessage message = mock(ChatMessage.class);

  @Test
  void executeTestWithArguments() {
    // Setup
    doReturn("*lastonline merc").when(message).getText();
    doReturn("*").when(engine).getPrefix();
    doReturn("testAuthor").when(message).getNick();
    
    LastOnlineUserCommandImpl cmd = new LastOnlineUserCommandImpl(engine, message, List.of());

    // Execute
    Optional<Status> result = cmd.execute();

    // Verify
    assertTrue(result.isPresent());
    assertEquals(Status.SUCCESSFUL, result.get());
    
    // Verify that the correct service method was called with proper arguments
    verify(engine.outService).enqueueMessageForSending(
        "testAuthor", 
        anyString(), 
        eq(false)
    );
  }

  @Test
  void executeTestWithoutArguments() {
    // Setup
    doReturn("*lastonline").when(message).getText();
    doReturn("*").when(engine).getPrefix();
    doReturn("testAuthor").when(message).getNick();
    
    LastOnlineUserCommandImpl cmd = new LastOnlineUserCommandImpl(engine, message, List.of());

    // Execute
    Optional<Status> result = cmd.execute();

    // Verify
    assertTrue(result.isPresent());
    assertEquals(Status.FAILED, result.get());
    
    // Verify that the correct service method was called with proper arguments
    verify(engine.outService).enqueueMessageForSending(
        "testAuthor", 
        anyString(), 
        eq(false)
    );
  }
}