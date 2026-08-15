package org.saturn.app.agent.persistence;

@FunctionalInterface
public interface AgentSchemaRepository {
  AgentDatabaseSchema describe();
}
