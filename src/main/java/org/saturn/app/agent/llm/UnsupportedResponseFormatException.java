package org.saturn.app.agent.llm;

/** Signals that a language-model response has an unsupported format. */
public final class UnsupportedResponseFormatException extends LlmException {
  public UnsupportedResponseFormatException(String message) {
    super(message);
  }
}
