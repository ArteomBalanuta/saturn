package org.saturn.app.agent;

import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

@FunctionalInterface
public interface AgentRoomAutomation {
  enum Outcome {
    PASS,
    CLAIMED
  }

  Outcome onMessage(ChatMessage message);

  default void onJoin(User user) {}

  static AgentRoomAutomation none() {
    return ignored -> Outcome.PASS;
  }
}
