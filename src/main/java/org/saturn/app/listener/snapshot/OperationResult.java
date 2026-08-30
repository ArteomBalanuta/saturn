package org.saturn.app.listener.snapshot;

import java.util.Objects;

public record OperationResult(OperationOutcome outcome, String reply) {
  public OperationResult {
    Objects.requireNonNull(outcome, "outcome");
  }

  public static OperationResult success() {
    return new OperationResult(OperationOutcome.SUCCESS, null);
  }

  public static OperationResult success(String reply) {
    return new OperationResult(OperationOutcome.SUCCESS, reply);
  }

  public static OperationResult empty(String reply) {
    return new OperationResult(OperationOutcome.EMPTY, reply);
  }

  public static OperationResult absent(String reply) {
    return new OperationResult(OperationOutcome.ABSENT_TARGET, reply);
  }

  public static OperationResult skipped() {
    return new OperationResult(OperationOutcome.SKIPPED, null);
  }

  public static OperationResult failed(String reply) {
    return new OperationResult(OperationOutcome.FAILED, reply);
  }
}
