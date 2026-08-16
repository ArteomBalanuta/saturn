package org.saturn.app.agent;

import com.moandjiezana.toml.Toml;
import org.saturn.app.agent.moderation.AgentModerationConfig;
import org.saturn.app.agent.tool.EngineSaturnCommandGateway;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.service.AgentService;
import org.saturn.app.service.impl.AgentServiceImpl;
import org.saturn.app.service.impl.OutService;

public final class AgentRuntimeFactory {
  private AgentRuntimeFactory() {}

  public static AgentService create(
      EngineImpl engine, Toml rootConfig, String databasePath, OutService outService) {
    AgentConfig config = AgentConfig.from(rootConfig, System.getenv());
    Runnable replyFlusher = engine == null ? () -> {} : engine::shareMessages;
    if (!config.enabled()) {
      AgentRouter disabledRouter =
          invocation -> {
            throw new AgentRoutingException("Agent is disabled");
          };
      AgentService service = new AgentServiceImpl(config, disabledRouter, outService, replyFlusher);
      if (engine != null) {
        engine.setAgentRoomAutomation(AgentRoomAutomation.none());
      }
      return service;
    }

    AgentSqlConfig sqlConfig = AgentSqlConfig.from(rootConfig, System.getenv());
    AgentParticipationConfig participationConfig = AgentParticipationConfig.from(rootConfig);
    AgentModerationConfig moderationConfig = AgentModerationConfig.from(rootConfig);
    AgentInfrastructure infrastructure = new AgentInfrastructureFactory().create(databasePath);
    EngineSaturnCommandGateway commandGateway = new EngineSaturnCommandGateway(engine);
    AgentToolRegistry registry =
        new AgentToolRegistryFactory().create(engine, infrastructure, sqlConfig, commandGateway);
    AgentRouter router =
        new AgentRouterFactory().create(config, registry, infrastructure, participationConfig);
    AgentService service = new AgentServiceImpl(config, router, outService, replyFlusher);
    if (engine != null) {
      engine.setAgentRoomAutomation(
          new AgentRoomAutomationFactory()
              .create(
                  engine,
                  participationConfig,
                  moderationConfig,
                  service,
                  commandGateway,
                  outService));
    }
    return service;
  }
}
