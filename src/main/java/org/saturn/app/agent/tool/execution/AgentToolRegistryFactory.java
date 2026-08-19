package org.saturn.app.agent.tool.execution;

import org.saturn.app.agent.config.AgentSqlConfig;
import org.saturn.app.agent.routing.AgentInfrastructure;
import org.saturn.app.agent.sql.JSqlParserAgentSqlPolicy;
import org.saturn.app.agent.tool.DatabaseQueryTool;
import org.saturn.app.agent.tool.DatabaseSchemaTool;
import org.saturn.app.agent.tool.DatabaseSqlTool;
import org.saturn.app.agent.tool.EngineAgentRoomDirectory;
import org.saturn.app.agent.tool.EngineSaturnCommandGateway;
import org.saturn.app.agent.tool.RoomUsersTool;
import org.saturn.app.agent.tool.RunCommandTool;
import org.saturn.app.agent.tool.SaturnCommandToolCatalog;
import org.saturn.app.agent.tool.UserMessageHistoryTool;
import org.saturn.app.facade.impl.EngineImpl;

/** Registers and freezes Saturn's built-in agent tool inventory. */
public final class AgentToolRegistryFactory {
  public AgentToolRegistry create(
      EngineImpl engine,
      AgentInfrastructure infrastructure,
      AgentSqlConfig sqlConfig,
      EngineSaturnCommandGateway commandGateway) {
    AgentToolRegistry registry =
        new AgentToolRegistry()
            .register(new RoomUsersTool(new EngineAgentRoomDirectory(engine)))
            .register(new DatabaseQueryTool(infrastructure.queryRepository()))
            .register(new UserMessageHistoryTool(infrastructure.queryRepository()))
            .register(new DatabaseSchemaTool(infrastructure.schemaRepository(), sqlConfig))
            .register(
                new DatabaseSqlTool(
                    infrastructure.schemaRepository(),
                    new JSqlParserAgentSqlPolicy(sqlConfig),
                    infrastructure.sqlRepository(),
                    sqlConfig))
            .register(new RunCommandTool(commandGateway));
    SaturnCommandToolCatalog.registerAll(registry, commandGateway);
    return registry.freeze();
  }
}
