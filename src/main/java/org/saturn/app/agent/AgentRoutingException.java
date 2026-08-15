package org.saturn.app.agent;

public class AgentRoutingException extends Exception {
  public AgentRoutingException(String message) {
    super(message);
  }

  public AgentRoutingException(String message, Throwable cause) {
    super(message, cause);
  }
}
