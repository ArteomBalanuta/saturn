package org.saturn.app.agent.persistence;

import org.saturn.app.agent.AgentSqlConfig;
import org.saturn.app.agent.sql.ValidatedAgentSql;

@FunctionalInterface
public interface AgentSqlRepository {
  AgentSqlResult execute(ValidatedAgentSql sql, AgentSqlConfig config);
}
