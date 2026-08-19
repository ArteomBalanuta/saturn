package org.saturn.app.agent.routing;

import org.saturn.app.agent.persistence.H2AgentMemoryStore;
import org.saturn.app.agent.persistence.H2AgentQueryRepository;
import org.saturn.app.agent.persistence.H2AgentSchemaRepository;
import org.saturn.app.agent.persistence.H2AgentSqlRepository;

/** Persistence adapters required by the agent runtime composition root. */
public record AgentInfrastructure(
    H2AgentQueryRepository queryRepository,
    H2AgentMemoryStore memoryStore,
    H2AgentSchemaRepository schemaRepository,
    H2AgentSqlRepository sqlRepository) {}
