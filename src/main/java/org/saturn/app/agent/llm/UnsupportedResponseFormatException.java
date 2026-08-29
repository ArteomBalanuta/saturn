package org.saturn.app.agent.llm;

/** Signals that a language-model response has an unsupported format. */
public final class UnsupportedResponseFormatException extends LlmException {
  /**
   * Implements the {@code UnsupportedResponseFormatException} operation for this agent component.
   *
   * @param message input argument used by this operation
   */
  public UnsupportedResponseFormatException(String message) {
    super(message);
  }
}
