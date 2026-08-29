package org.saturn.app.agent.api;

/** Signals that an agent request could not be routed or prepared. */
public class AgentRoutingException extends Exception {
  /**
   * Implements the {@code AgentRoutingException} operation for this agent component.
   *
   * @param message input argument used by this operation
   */
  public AgentRoutingException(String message) {
    super(message);
  }

  /**
   * Implements the {@code AgentRoutingException} operation for this agent component.
   *
   * @param message input argument used by this operation
   * @param cause input argument used by this operation
   */
  public AgentRoutingException(String message, Throwable cause) {
    super(message, cause);
  }
}
