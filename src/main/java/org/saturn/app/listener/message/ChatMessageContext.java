package org.saturn.app.listener.message;

import java.util.Optional;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

public class ChatMessageContext {
  private final EngineImpl engine;
  private final ChatMessage message;
  private Optional<User> author = Optional.empty();

  public ChatMessageContext(EngineImpl engine, ChatMessage message) {
    this.engine = engine;
    this.message = message;
  }

  public EngineImpl getEngine() {
    return engine;
  }

  public ChatMessage getMessage() {
    return message;
  }

  public Optional<User> getAuthor() {
    return author;
  }

  public void setAuthor(Optional<User> author) {
    this.author = author;
  }
}
