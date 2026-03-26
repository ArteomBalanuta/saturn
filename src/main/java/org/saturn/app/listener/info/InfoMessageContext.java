package org.saturn.app.listener.info;

import java.util.Optional;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.model.dto.payload.InfoMessage;

public class InfoMessageContext {
  private final EngineImpl engine;
  private final InfoMessage infoMessage;
  private Optional<ChatMessage> chatMessage = Optional.empty();

  public InfoMessageContext(EngineImpl engine, InfoMessage infoMessage) {
    this.engine = engine;
    this.infoMessage = infoMessage;
  }

  public EngineImpl getEngine() {
    return engine;
  }

  public InfoMessage getInfoMessage() {
    return infoMessage;
  }

  public Optional<ChatMessage> getChatMessage() {
    return chatMessage;
  }

  public void setChatMessage(Optional<ChatMessage> chatMessage) {
    this.chatMessage = chatMessage;
  }
}
