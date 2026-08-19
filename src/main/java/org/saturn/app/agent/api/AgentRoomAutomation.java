package org.saturn.app.agent.api;

import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

@FunctionalInterface
/** Defines the contract for automating agent participation in a room. */
public interface AgentRoomAutomation {
  /** Enumerates the possible outcome states used by the enclosing agent component. */
  /** Enumerates the possible outcome states used by the enclosing agent component. */
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
