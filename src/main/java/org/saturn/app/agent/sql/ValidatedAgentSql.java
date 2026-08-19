package org.saturn.app.agent.sql;

import java.util.Objects;

/** Represents an SQL statement that passed agent SQL policy validation. */
public record ValidatedAgentSql(String sql, String fingerprint) {
  public ValidatedAgentSql {
    Objects.requireNonNull(sql, "sql");
    Objects.requireNonNull(fingerprint, "fingerprint");
  }
}
