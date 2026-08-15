package org.saturn.app.agent;

import com.moandjiezana.toml.Toml;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import org.saturn.app.agent.llm.OpenAiCompatibleClient;
import org.saturn.app.agent.moderation.AgentModerationConfig;
import org.saturn.app.agent.moderation.EngineModerationActionExecutor;
import org.saturn.app.agent.moderation.RoomModerationMonitor;
import org.saturn.app.agent.persistence.AgentSchemaMigrator;
import org.saturn.app.agent.persistence.RepositoryAgentConversationContextProvider;
import org.saturn.app.agent.persistence.SqliteAgentMemoryStore;
import org.saturn.app.agent.persistence.SqliteAgentQueryRepository;
import org.saturn.app.agent.persistence.SqliteAgentSchemaRepository;
import org.saturn.app.agent.persistence.SqliteAgentSqlRepository;
import org.saturn.app.agent.persistence.SqliteReadOnlyConnectionFactory;
import org.saturn.app.agent.sql.JSqlParserAgentSqlPolicy;
import org.saturn.app.agent.tool.DatabaseQueryTool;
import org.saturn.app.agent.tool.DatabaseSchemaTool;
import org.saturn.app.agent.tool.DatabaseSqlTool;
import org.saturn.app.agent.tool.EngineAgentRoomDirectory;
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

    AgentSchemaMigrator.migrate(databasePath);
    AgentSqlConfig sqlConfig = AgentSqlConfig.from(rootConfig, System.getenv());
    AgentParticipationConfig participationConfig = AgentParticipationConfig.from(rootConfig);
    AgentModerationConfig moderationConfig = AgentModerationConfig.from(rootConfig);
    var queryRepository = new SqliteAgentQueryRepository(databasePath);
    var memoryStore = new SqliteAgentMemoryStore(databasePath);
    var readOnlyConnectionFactory = new SqliteReadOnlyConnectionFactory(databasePath);
    var schemaRepository = new SqliteAgentSchemaRepository(readOnlyConnectionFactory);
    var sqlRepository = new SqliteAgentSqlRepository(readOnlyConnectionFactory);
    EngineSaturnCommandGateway commandGateway = new EngineSaturnCommandGateway(engine);
    AgentToolRegistry registry =
        new AgentToolRegistry()
            .register(new RoomUsersTool(new EngineAgentRoomDirectory(engine)))
            .register(new DatabaseQueryTool(queryRepository))
            .register(new UserMessageHistoryTool(queryRepository))
            .register(new DatabaseSchemaTool(schemaRepository, sqlConfig))
            .register(
                new DatabaseSqlTool(
                    schemaRepository,
                    new JSqlParserAgentSqlPolicy(sqlConfig),
                    sqlRepository,
                    sqlConfig))
            .register(new RunCommandTool(commandGateway))
            .freeze();
    AgentRouter router =
        new DefaultAgentRouter(
            config,
            new OpenAiCompatibleClient(config),
            registry,
            memoryStore,
            participationConfig,
            new RepositoryAgentConversationContextProvider(
                queryRepository, participationConfig.contextMessageLimit()));
    AgentService service = new AgentServiceImpl(config, router, outService, replyFlusher);
    if (engine != null) {
      Set<String> protectedTrips = protectedTrips(engine, participationConfig.creatorTrip());
      RoomModerationMonitor moderationMonitor =
          new RoomModerationMonitor(
              moderationConfig,
              Clock.systemUTC(),
              message ->
                  isProtectedTrip(protectedTrips, message.getTrip())
                      || isProtectedNick(engine, message.getNick()),
              user ->
                  user.isIsMe()
                      || user.isBot()
                      || isProtectedTrip(protectedTrips, user.getTrip())
                      || isProtectedNick(engine, user.getNick()));
      AgentContext botContext =
          new AgentContext(
              engine.channel,
              engine.nick,
              participationConfig.creatorTrip(),
              null,
              false,
              engine.currentChannelUsers.stream().map(user -> user.getNick()).toList(),
              Set.of(AgentCapability.MODERATION_COMMANDS));
      engine.setAgentRoomAutomation(
          new DefaultAgentRoomAutomation(
              engine,
              participationConfig,
              service,
              new AgentInvocationFactory(participationConfig),
              new AgentMentionParser(),
              new AgentQuietRegistry(participationConfig.quietDuration(), Clock.systemUTC()),
              moderationMonitor,
              new EngineModerationActionExecutor(commandGateway, outService, botContext),
              botContext,
              message ->
                  moderationConfig.enabled()
                      && !isProtectedTrip(protectedTrips, message.getTrip())
                      && !isProtectedNick(engine, message.getNick())));
    }
    return service;
  }

  private static Set<String> protectedTrips(EngineImpl engine, String creatorTrip) {
    Set<String> trips = new HashSet<>();
    trips.add(creatorTrip);
    if (engine.adminTrips != null) {
      for (String configuredTrip : engine.adminTrips.split(",")) {
        if (!configuredTrip.isBlank()) {
          trips.add(configuredTrip.strip());
        }
      }
    }
    return Set.copyOf(trips);
  }

  private static boolean isProtectedTrip(Set<String> protectedTrips, String trip) {
    return trip != null && protectedTrips.contains(trip);
  }

  private static boolean isProtectedNick(EngineImpl engine, String nick) {
    if (nick == null || nick.isBlank() || engine.nick.equalsIgnoreCase(nick)) {
      return true;
    }
    EngineImpl root = engine.getHostRef() == null ? engine : engine.getHostRef();
    if (root.nick != null && root.nick.equalsIgnoreCase(nick)) {
      return true;
    }
    for (EngineImpl replica : root.replicasMappedByChannel.values()) {
      if (replica.nick != null && replica.nick.equalsIgnoreCase(nick)) {
        return true;
      }
    }
    return false;
  }
}
