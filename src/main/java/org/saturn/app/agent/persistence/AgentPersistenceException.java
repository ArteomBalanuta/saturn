package org.saturn.app.agent.persistence;

import java.util.Objects;
import org.saturn.app.agent.sql.AgentSqlErrorCode;

public class AgentPersistenceException extends RuntimeException {
  private final AgentSqlErrorCode code;

  public AgentPersistenceException(String message, Throwable cause) {
    this(AgentSqlErrorCode.EXECUTION_FAILED, message, cause);
  }

  public AgentPersistenceException(AgentSqlErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = Objects.requireNonNull(code, "code");
  }

  public AgentSqlErrorCode code() {
    return code;
  }
}
