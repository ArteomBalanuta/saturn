package org.saturn.app.agent.llm;

/** Signals a failure while communicating with or processing a language model. */
public class LlmException extends Exception {
  /**
   * Implements the {@code LlmException} operation for this agent component.
   *
   * @param message input argument used by this operation
   */
  public LlmException(String message) {
    super(message);
  }

  /**
   * Implements the {@code LlmException} operation for this agent component.
   *
   * @param message input argument used by this operation
   * @param cause input argument used by this operation
   */
  public LlmException(String message, Throwable cause) {
    super(message, cause);
  }
}
