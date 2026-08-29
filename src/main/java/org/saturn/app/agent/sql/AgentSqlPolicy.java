package org.saturn.app.agent.sql;

import org.saturn.app.agent.persistence.AgentDatabaseSchema;

@FunctionalInterface
/** Defines the policy used to validate agent SQL statements. */
public interface AgentSqlPolicy {
  ValidatedAgentSql validate(String sql, AgentDatabaseSchema schema);
}
