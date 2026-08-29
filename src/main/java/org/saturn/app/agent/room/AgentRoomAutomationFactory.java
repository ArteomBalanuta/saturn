package org.saturn.app.agent.room;

import java.time.Clock;
import java.util.Set;
import org.saturn.app.agent.api.AgentCapability;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.agent.api.AgentRoomAutomation;
import org.saturn.app.agent.moderation.AgentModerationConfig;
import org.saturn.app.agent.moderation.EngineModerationActionExecutor;
import org.saturn.app.agent.moderation.RoomModerationMonitor;
import org.saturn.app.agent.routing.AgentInvocationFactory;
import org.saturn.app.agent.tool.EngineSaturnCommandGateway;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.service.AgentService;
import org.saturn.app.service.impl.OutService;

/** Composes autonomous moderation and room participation around an active engine. */
public final class AgentRoomAutomationFactory {
  /**
   * Implements the {@code create} operation for this agent component.
   *
   * @param engine input argument used by this operation
   * @param participationConfig input argument used by this operation
   * @param moderationConfig input argument used by this operation
   * @param service input argument used by this operation
   * @param commandGateway input argument used by this operation
   * @param outService input argument used by this operation
   * @return the operation result
   */
  public AgentRoomAutomation create(
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
