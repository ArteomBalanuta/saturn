package org.saturn.app.agent.routing;

import org.saturn.app.agent.persistence.H2AgentMemoryStore;
import org.saturn.app.agent.persistence.H2AgentQueryRepository;
import org.saturn.app.agent.persistence.H2AgentSchemaRepository;
import org.saturn.app.agent.persistence.H2AgentSqlRepository;
import org.saturn.app.agent.persistence.H2ReadOnlyConnectionFactory;

/** Creates the persistence adapter graph used by the agent runtime. */
final class AgentInfrastructureFactory {
  AgentInfrastructure create(String databasePath) {
    H2ReadOnlyConnectionFactory connections = new H2ReadOnlyConnectionFactory(databasePath);
    return new AgentInfrastructure(
        new H2AgentQueryRepository(databasePath),
        new H2AgentMemoryStore(databasePath),
        new H2AgentSchemaRepository(connections),
        new H2AgentSqlRepository(connections));
  }
}
