package org.saturn.app.agent.room;

import java.util.function.Predicate;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.agent.api.AgentRoomAutomation;
import org.saturn.app.agent.moderation.ModerationActionExecutor;
import org.saturn.app.agent.moderation.RoomModerationMonitor;
import org.saturn.app.agent.routing.AgentInvocationFactory;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.AgentService;

/** Provides the default room automation implementation for agent participation. */
public final class DefaultAgentRoomAutomation implements AgentRoomAutomation {
  private final AgentRoomMessagePipeline pipeline;

  /** Creates room automation with moderation disabled. */
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

  /** Creates room automation with explicit moderation components. */
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

  /** Creates room automation with explicit moderation and candidate-selection policies. */
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

  /** Delegates an incoming room message to the pipeline. */
  @Override
  public Outcome onMessage(ChatMessage message) {
    return pipeline.onMessage(message);
  }

  /** Delegates a room-join event to the pipeline. */
  @Override
  public void onJoin(User user) {
    pipeline.onJoin(user);
  }
}
