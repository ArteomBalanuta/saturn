package org.saturn.app.agent.persistence;

@FunctionalInterface
/** Provides database schema metadata to agent persistence and tools. */
public interface AgentSchemaRepository {
  AgentDatabaseSchema describe();
}
