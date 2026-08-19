package org.saturn.app.agent.api;

/** Signals that an agent request could not be routed or prepared. */
public class AgentRoutingException extends Exception {
  public AgentRoutingException(String message) {
    super(message);
  }

  public AgentRoutingException(String message, Throwable cause) {
    super(message, cause);
  }
}
