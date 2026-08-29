package org.saturn.app.agent.sql;

import java.util.Objects;

/** Signals that an SQL statement violates the agent SQL policy. */
public final class AgentSqlPolicyException extends RuntimeException {
  private final AgentSqlErrorCode code;

  /**
   * Implements the {@code AgentSqlPolicyException} operation for this agent component.
   *
   * @param code input argument used by this operation
   * @param message input argument used by this operation
   */
  public AgentSqlPolicyException(AgentSqlErrorCode code, String message) {
    super(message);
    this.code = Objects.requireNonNull(code, "code");
  }

  /**
   * Implements the {@code AgentSqlPolicyException} operation for this agent component.
   *
   * @param code input argument used by this operation
   * @param message input argument used by this operation
   * @param cause input argument used by this operation
   */
  public AgentSqlPolicyException(AgentSqlErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = Objects.requireNonNull(code, "code");
  }

  /**
   * Implements the {@code code} operation for this agent component.
   *
   * @return the operation result
   */
  public AgentSqlErrorCode code() {
    return code;
  }
}
