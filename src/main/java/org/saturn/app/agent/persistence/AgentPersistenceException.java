package org.saturn.app.agent.persistence;

import java.util.Objects;
import org.saturn.app.agent.sql.AgentSqlErrorCode;

/** Signals a failure while reading or writing agent persistence data. */
public class AgentPersistenceException extends RuntimeException {
  private final AgentSqlErrorCode code;

  /**
   * Implements the {@code AgentPersistenceException} operation for this agent component.
   *
   * @param message input argument used by this operation
   * @param cause input argument used by this operation
   */
  public AgentPersistenceException(String message, Throwable cause) {
    this(AgentSqlErrorCode.EXECUTION_FAILED, message, cause);
  }

  /**
   * Implements the {@code AgentPersistenceException} operation for this agent component.
   *
   * @param code input argument used by this operation
   * @param message input argument used by this operation
   * @param cause input argument used by this operation
   */
  public AgentPersistenceException(AgentSqlErrorCode code, String message, Throwable cause) {
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
