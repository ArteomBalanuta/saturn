package org.saturn.app.agent;

import java.time.Clock;
import java.util.Set;
import org.saturn.app.agent.moderation.AgentModerationConfig;
import org.saturn.app.agent.moderation.EngineModerationActionExecutor;
import org.saturn.app.agent.moderation.RoomModerationMonitor;
import org.saturn.app.agent.tool.EngineSaturnCommandGateway;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.service.AgentService;
import org.saturn.app.service.impl.OutService;

/** Composes autonomous moderation and room participation around an active engine. */
final class AgentRoomAutomationFactory {
  AgentRoomAutomation create(
      EngineImpl engine,
      AgentParticipationConfig participationConfig,
      AgentModerationConfig moderationConfig,
      AgentService service,
      EngineSaturnCommandGateway commandGateway,
      OutService outService) {
    ProtectedPrincipalPolicy protectedPrincipals =
        new ProtectedPrincipalPolicy(engine, participationConfig.creatorTrip());
    RoomModerationMonitor moderationMonitor =
        new RoomModerationMonitor(
            moderationConfig,
            Clock.systemUTC(),
            message -> protectedPrincipals.isProtected(message.getTrip(), message.getNick()),
            protectedPrincipals::isProtected);
    AgentContext botContext =
        new AgentContext(
            engine.channel,
            engine.nick,
            participationConfig.creatorTrip(),
            null,
            false,
            engine.currentChannelUsers.stream().map(user -> user.getNick()).toList(),
            Set.of(AgentCapability.MODERATION_COMMANDS));
    return new DefaultAgentRoomAutomation(
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
                && !protectedPrincipals.isProtected(message.getTrip(), message.getNick()));
  }
}
