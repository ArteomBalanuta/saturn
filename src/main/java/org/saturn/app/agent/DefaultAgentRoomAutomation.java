package org.saturn.app.agent;

import java.util.function.Predicate;
import org.saturn.app.agent.moderation.ModerationActionExecutor;
import org.saturn.app.agent.moderation.RoomModerationMonitor;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.AgentService;

public final class DefaultAgentRoomAutomation implements AgentRoomAutomation {
  private final AgentRoomMessagePipeline pipeline;

  public DefaultAgentRoomAutomation(
      EngineImpl engine,
      AgentParticipationConfig config,
      AgentService agentService,
      AgentInvocationFactory invocationFactory,
      AgentMentionParser mentionParser,
      AgentQuietRegistry quietRegistry) {
    this(
        engine,
        config,
        agentService,
        invocationFactory,
        mentionParser,
        quietRegistry,
        RoomModerationMonitor.disabled(),
        ModerationActionExecutor.none(),
        null,
        message -> false);
  }

  public DefaultAgentRoomAutomation(
      EngineImpl engine,
      AgentParticipationConfig config,
      AgentService agentService,
      AgentInvocationFactory invocationFactory,
      AgentMentionParser mentionParser,
      AgentQuietRegistry quietRegistry,
      RoomModerationMonitor moderationMonitor,
      ModerationActionExecutor moderationExecutor) {
    this(
        engine,
        config,
        agentService,
        invocationFactory,
        mentionParser,
        quietRegistry,
        moderationMonitor,
        moderationExecutor,
        null,
        message -> false);
  }

  public DefaultAgentRoomAutomation(
      EngineImpl engine,
      AgentParticipationConfig config,
      AgentService agentService,
      AgentInvocationFactory invocationFactory,
      AgentMentionParser mentionParser,
      AgentQuietRegistry quietRegistry,
      RoomModerationMonitor moderationMonitor,
      ModerationActionExecutor moderationExecutor,
      AgentContext botModerationContext,
      Predicate<ChatMessage> semanticModerationCandidate) {
    this.pipeline =
        new AgentRoomMessagePipeline(
            engine,
            config,
            agentService,
            invocationFactory,
            mentionParser,
            quietRegistry,
            moderationMonitor,
            moderationExecutor,
            botModerationContext,
            semanticModerationCandidate);
  }

  @Override
  public Outcome onMessage(ChatMessage message) {
    return pipeline.onMessage(message);
  }

  @Override
  public void onJoin(User user) {
    pipeline.onJoin(user);
  }
}
