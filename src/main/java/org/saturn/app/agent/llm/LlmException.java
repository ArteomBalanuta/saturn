package org.saturn.app.agent.llm;

/** Signals a failure while communicating with or processing a language model. */
public class LlmException extends Exception {
  public LlmException(String message) {
    super(message);
  }

  public LlmException(String message, Throwable cause) {
    super(message, cause);
  }
}
