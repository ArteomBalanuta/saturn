package org.saturn.app.agent.routing;

import com.moandjiezana.toml.Toml;
import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.agent.api.AgentRoomAutomation;
import org.saturn.app.agent.api.AgentRouter;
import org.saturn.app.agent.api.AgentRoutingException;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.agent.config.AgentConfigLoader;
import org.saturn.app.agent.config.AgentSqlConfig;
import org.saturn.app.agent.moderation.AgentModerationConfig;
import org.saturn.app.agent.room.AgentRoomAutomationFactory;
import org.saturn.app.agent.tool.EngineSaturnCommandGateway;
import org.saturn.app.agent.tool.execution.AgentToolRegistry;
import org.saturn.app.agent.tool.execution.AgentToolRegistryFactory;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.service.AgentService;
import org.saturn.app.service.impl.AgentServiceImpl;
import org.saturn.app.service.impl.OutService;

/** Builds the runtime collaborators used to execute agent turns. */
public final class AgentRuntimeFactory {
  /** Implements the {@code AgentRuntimeFactory} operation for this agent component. */
  private AgentRuntimeFactory() {}

  /**
   * Implements the {@code create} operation for this agent component.
   *
   * @param engine input argument used by this operation
   * @param rootConfig input argument used by this operation
   * @param databasePath input argument used by this operation
   * @param outService input argument used by this operation
   * @return the operation result
   */
  public static AgentService create(
      EngineImpl engine, Toml rootConfig, String databasePath, OutService outService) {
    AgentConfig config = AgentConfigLoader.load(rootConfig, System.getenv());
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
