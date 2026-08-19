package org.saturn.app.agent.persistence;

import org.saturn.app.agent.config.AgentSqlConfig;
import org.saturn.app.agent.sql.ValidatedAgentSql;

@FunctionalInterface
/** Provides SQL execution operations for agent persistence and tools. */
public interface AgentSqlRepository {
  AgentSqlResult execute(ValidatedAgentSql sql, AgentSqlConfig config);
}
