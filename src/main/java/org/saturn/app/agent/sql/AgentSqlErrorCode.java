package org.saturn.app.agent.sql;

/** Defines error codes reported by agent SQL validation and execution. */
public enum AgentSqlErrorCode {
  EMPTY_SQL,
  SQL_TOO_LONG,
  MALFORMED_SQL,
  FORBIDDEN_STATEMENT,
  FORBIDDEN_TABLE,
  FORBIDDEN_FUNCTION,
  TIMEOUT,
  RESULT_TOO_LARGE,
  EXECUTION_FAILED
}
