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

  /**
   * Implements the {@code onJoin} operation for this agent component.
   *
   * @param user input argument used by this operation
   */
  default void onJoin(User user) {}

  /**
   * Implements the {@code none} operation for this agent component.
   *
   * @return the operation result
   */
  static AgentRoomAutomation none() {
    return ignored -> Outcome.PASS;
  }
}
