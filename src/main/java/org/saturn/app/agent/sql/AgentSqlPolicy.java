package org.saturn.app.agent.sql;

import org.saturn.app.agent.persistence.AgentDatabaseSchema;

@FunctionalInterface
public interface AgentSqlPolicy {
  ValidatedAgentSql validate(String sql, AgentDatabaseSchema schema);
}
