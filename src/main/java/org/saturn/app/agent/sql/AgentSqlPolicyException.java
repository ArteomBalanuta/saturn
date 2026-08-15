package org.saturn.app.agent.sql;

import java.util.Objects;

public final class AgentSqlPolicyException extends RuntimeException {
  private final AgentSqlErrorCode code;

  public AgentSqlPolicyException(AgentSqlErrorCode code, String message) {
    super(message);
    this.code = Objects.requireNonNull(code, "code");
  }

  public AgentSqlPolicyException(AgentSqlErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = Objects.requireNonNull(code, "code");
  }

  public AgentSqlErrorCode code() {
    return code;
  }
}
