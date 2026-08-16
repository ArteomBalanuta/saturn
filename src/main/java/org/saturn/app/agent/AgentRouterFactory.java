package org.saturn.app.agent;

import org.saturn.app.agent.llm.OpenAiCompatibleClient;
import org.saturn.app.agent.persistence.RepositoryAgentConversationContextProvider;

/** Composes the provider, memory, room context, and routing policies. */
final class AgentRouterFactory {
  AgentRouter create(
      AgentConfig config,
      AgentToolRegistry registry,
      AgentInfrastructure infrastructure,
      AgentParticipationConfig participationConfig) {
    return new DefaultAgentRouter(
        config,
        new OpenAiCompatibleClient(config),
        registry,
        infrastructure.memoryStore(),
        participationConfig,
        new RepositoryAgentConversationContextProvider(
            infrastructure.queryRepository(), participationConfig.contextMessageLimit()));
  }
}
