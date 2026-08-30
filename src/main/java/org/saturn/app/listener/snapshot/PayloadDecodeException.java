package org.saturn.app.listener.snapshot;

public class PayloadDecodeException extends Exception {
  public PayloadDecodeException(String message, Throwable cause) {
    super(message, cause);
  }

  public PayloadDecodeException(String message) {
    super(message);
  }
}
