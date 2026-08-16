package org.saturn.app.agent;

import org.saturn.app.agent.persistence.H2AgentMemoryStore;
import org.saturn.app.agent.persistence.H2AgentQueryRepository;
import org.saturn.app.agent.persistence.H2AgentSchemaRepository;
import org.saturn.app.agent.persistence.H2AgentSqlRepository;

/** Persistence adapters required by the agent runtime composition root. */
record AgentInfrastructure(
    H2AgentQueryRepository queryRepository,
    H2AgentMemoryStore memoryStore,
    H2AgentSchemaRepository schemaRepository,
    H2AgentSqlRepository sqlRepository) {}
