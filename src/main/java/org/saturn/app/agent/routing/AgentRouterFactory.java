package org.saturn.app.agent.routing;

import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.agent.api.AgentRouter;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.agent.llm.provider.openai.OpenAiCompatibleClient;
import org.saturn.app.agent.persistence.RepositoryAgentConversationContextProvider;
import org.saturn.app.agent.tool.execution.AgentToolExecutionHooks;
import org.saturn.app.agent.tool.execution.AgentToolRegistry;

/** Composes the provider, memory, room context, and routing policies. */
final class AgentRouterFactory {
  AgentRouter create(
      AgentConfig config,
      AgentToolRegistry registry,
      AgentInfrastructure infrastructure,
      AgentParticipationConfig participationConfig) {
    AgentToolExecutionHooks hooks =
        new AgentToolExecutionHooks(
            java.util.List.of(),
            java.util.List.of(
                (context, result) ->
                    org.slf4j.LoggerFactory.getLogger(AgentRouterFactory.class)
                        .debug(
                            "agent tool outcome tool={} code={}",
                            context.call().name(),
                            result.errorCode())));
    return new DefaultAgentRouter(
        config,
        new OpenAiCompatibleClient(config),
        registry,
        infrastructure.memoryStore(),
        participationConfig,
        new RepositoryAgentConversationContextProvider(
            infrastructure.queryRepository(), participationConfig.contextMessageLimit()),
        hooks);
  }
}
