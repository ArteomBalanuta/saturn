package org.saturn.app.agent.llm;

public final class UnsupportedResponseFormatException extends LlmException {
  public UnsupportedResponseFormatException(String message) {
    super(message);
  }
}
