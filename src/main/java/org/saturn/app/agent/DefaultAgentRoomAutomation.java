package org.saturn.app.agent;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.moderation.ModerationActionExecutor;
import org.saturn.app.agent.moderation.ModerationDecision;
import org.saturn.app.agent.moderation.RoomModerationMonitor;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.AgentService;

@Slf4j
public final class DefaultAgentRoomAutomation implements AgentRoomAutomation {
  private static final Pattern CONVENTIONAL_BOT_NICK = Pattern.compile("(?iu)bot(?:[_-]?\\d+)?$");

  private final EngineImpl engine;
  private final AgentParticipationConfig config;
  private final AgentService agentService;
  private final AgentInvocationFactory invocationFactory;
  private final AgentMentionParser mentionParser;
  private final AgentQuietRegistry quietRegistry;
  private final RoomModerationMonitor moderationMonitor;
  private final ModerationActionExecutor moderationExecutor;
  private final AtomicLong eligibleAmbientMessages = new AtomicLong();

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
        ModerationActionExecutor.none());
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
    this.engine = Objects.requireNonNull(engine, "engine");
    this.config = Objects.requireNonNull(config, "config");
    this.agentService = Objects.requireNonNull(agentService, "agentService");
    this.invocationFactory = Objects.requireNonNull(invocationFactory, "invocationFactory");
    this.mentionParser = Objects.requireNonNull(mentionParser, "mentionParser");
    this.quietRegistry = Objects.requireNonNull(quietRegistry, "quietRegistry");
    this.moderationMonitor = Objects.requireNonNull(moderationMonitor, "moderationMonitor");
    this.moderationExecutor = Objects.requireNonNull(moderationExecutor, "moderationExecutor");
  }

  @Override
  public Outcome onMessage(ChatMessage message) {
    execute(moderationMonitor.onMessage(message));
    String text = message.getText().strip();
    if (text.isEmpty()
        || message.isWhisper()
        || engine.nick.equalsIgnoreCase(message.getNick())
        || isBotAuthor(message.getNick())
        || text.startsWith(engine.prefix)) {
      return Outcome.PASS;
    }

    Optional<String> mentionPrompt = mentionParser.parse(text, engine.nick);
    AgentInvocationMode mode =
        mentionPrompt.isPresent() ? AgentInvocationMode.MENTION : AgentInvocationMode.AMBIENT;
    String prompt = mentionPrompt.orElse(text);
    AgentInvocation invocation = invocationFactory.create(engine, message, prompt, mode);

    boolean quietRequest = quietRegistry.isPoliteQuietRequest(text, engine.nick);
    if (quietRequest) {
      quietRegistry.silence(invocation.context());
      return Outcome.PASS;
    }
    if (mentionPrompt.isPresent()) {
      agentService.submit(invocation);
      return Outcome.CLAIMED;
    }
    if (!config.ambientEnabled() || quietRegistry.isQuiet(invocation.context())) {
      return Outcome.PASS;
    }
    if (Math.floorMod(eligibleAmbientMessages.incrementAndGet(), config.ambientEveryMessages())
        != 0) {
      return Outcome.PASS;
    }

    agentService.submit(invocation);
    return Outcome.PASS;
  }

  @Override
  public void onJoin(org.saturn.app.model.dto.User user) {
    execute(moderationMonitor.onJoin(user));
  }

  private void execute(java.util.List<ModerationDecision> decisions) {
    for (ModerationDecision decision : decisions) {
      if (!moderationExecutor.execute(decision)) {
        log.warn(
            "Autonomous moderation decision was not executed, action={}, target={}",
            decision.action(),
            decision.target().orElse("room"));
      }
    }
  }

  private boolean isBotAuthor(String nick) {
    return CONVENTIONAL_BOT_NICK.matcher(nick).find()
        || engine.currentChannelUsers.stream()
            .anyMatch(user -> user.isBot() && user.getNick().equalsIgnoreCase(nick));
  }
}
