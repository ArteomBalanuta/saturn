package org.saturn.app.agent;

import com.moandjiezana.toml.Toml;
import org.saturn.app.agent.llm.OpenAiCompatibleClient;
import org.saturn.app.agent.persistence.AgentSchemaMigrator;
import org.saturn.app.agent.persistence.SqliteAgentMemoryStore;
import org.saturn.app.agent.persistence.SqliteAgentQueryRepository;
import org.saturn.app.agent.persistence.SqliteAgentSchemaRepository;
import org.saturn.app.agent.persistence.SqliteAgentSqlRepository;
import org.saturn.app.agent.persistence.SqliteReadOnlyConnectionFactory;
import org.saturn.app.agent.sql.JSqlParserAgentSqlPolicy;
import org.saturn.app.agent.tool.DatabaseQueryTool;
import org.saturn.app.agent.tool.DatabaseSchemaTool;
import org.saturn.app.agent.tool.DatabaseSqlTool;
import org.saturn.app.agent.tool.EngineSaturnCommandGateway;
import org.saturn.app.agent.tool.RoomUsersTool;
import org.saturn.app.agent.tool.RunCommandTool;
import org.saturn.app.agent.tool.UserMessageHistoryTool;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.service.AgentService;
import org.saturn.app.service.impl.AgentServiceImpl;
import org.saturn.app.service.impl.OutService;

public final class AgentRuntimeFactory {
  private AgentRuntimeFactory() {}

  public static AgentService create(
      EngineImpl engine, Toml rootConfig, String databasePath, OutService outService) {
    AgentConfig config = AgentConfig.from(rootConfig, System.getenv());
    if (!config.enabled()) {
      AgentRouter disabledRouter =
          invocation -> {
            throw new AgentRoutingException("Agent is disabled");
          };
      return new AgentServiceImpl(config, disabledRouter, outService);
    }

    AgentSchemaMigrator.migrate(databasePath);
    AgentSqlConfig sqlConfig = AgentSqlConfig.from(rootConfig);
    var queryRepository = new SqliteAgentQueryRepository(databasePath);
    var memoryStore = new SqliteAgentMemoryStore(databasePath);
    var readOnlyConnectionFactory = new SqliteReadOnlyConnectionFactory(databasePath);
    var schemaRepository = new SqliteAgentSchemaRepository(readOnlyConnectionFactory);
    var sqlRepository = new SqliteAgentSqlRepository(readOnlyConnectionFactory);
    AgentToolRegistry registry =
        new AgentToolRegistry()
            .register(new RoomUsersTool())
            .register(new DatabaseQueryTool(queryRepository))
            .register(new UserMessageHistoryTool(queryRepository))
            .register(new DatabaseSchemaTool(schemaRepository, sqlConfig))
            .register(
                new DatabaseSqlTool(
                    schemaRepository,
                    new JSqlParserAgentSqlPolicy(sqlConfig),
                    sqlRepository,
                    sqlConfig))
            .register(new RunCommandTool(new EngineSaturnCommandGateway(engine)))
            .freeze();
    AgentRouter router =
        new DefaultAgentRouter(config, new OpenAiCompatibleClient(config), registry, memoryStore);
    return new AgentServiceImpl(config, router, outService);
  }
}
