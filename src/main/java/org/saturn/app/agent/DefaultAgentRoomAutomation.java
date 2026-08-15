package org.saturn.app.agent;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
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
  private static final Pattern CONVENTIONAL_BOT_NICK =
      Pattern.compile("(?u)^(?:bot(?:[_-]?\\d+)?|[\\p{L}\\p{N}_-]*(?:Bot|[_-]bot)(?:[_-]?\\d+)?)$");
  private static final Pattern SEMANTIC_MODERATION_SIGNAL =
      Pattern.compile(
          "(?iu)\\b(?:kys|kill\\s+(?:yourself|urself|u|you)|hang\\s+(?:yourself|"
              + "urself)|doxx?\\b|swat(?:ting)?\\b|rape\\b|shoot\\s+you|stab\\s+you|"
              + "bomb\\s+(?:you|them|the room))");

  private final EngineImpl engine;
  private final AgentParticipationConfig config;
  private final AgentService agentService;
  private final AgentInvocationFactory invocationFactory;
  private final AgentMentionParser mentionParser;
  private final AgentQuietRegistry quietRegistry;
  private final RoomModerationMonitor moderationMonitor;
  private final ModerationActionExecutor moderationExecutor;
  private final AgentContext botModerationContext;
  private final Predicate<ChatMessage> semanticModerationCandidate;
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
    this.engine = Objects.requireNonNull(engine, "engine");
    this.config = Objects.requireNonNull(config, "config");
    this.agentService = Objects.requireNonNull(agentService, "agentService");
    this.invocationFactory = Objects.requireNonNull(invocationFactory, "invocationFactory");
    this.mentionParser = Objects.requireNonNull(mentionParser, "mentionParser");
    this.quietRegistry = Objects.requireNonNull(quietRegistry, "quietRegistry");
    this.moderationMonitor = Objects.requireNonNull(moderationMonitor, "moderationMonitor");
    this.moderationExecutor = Objects.requireNonNull(moderationExecutor, "moderationExecutor");
    this.botModerationContext = botModerationContext;
    this.semanticModerationCandidate =
        Objects.requireNonNull(semanticModerationCandidate, "semanticModerationCandidate");
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
    submitSemanticModeration(message, text);
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

  private void submitSemanticModeration(ChatMessage message, String text) {
    if (botModerationContext == null
        || !semanticModerationCandidate.test(message)
        || !requiresSemanticModeration(text)) {
      return;
    }
    AgentContext liveBotContext =
        new AgentContext(
            engine.channel,
            engine.nick,
            botModerationContext.trip(),
            botModerationContext.hash(),
            false,
            engine.currentChannelUsers.stream().map(user -> user.getNick()).toList(),
            botModerationContext.capabilities(),
            message.getNick());
    String prompt =
        "Review this public chat message for severe abuse. Author nick: %s. Message: %s"
            .formatted(message.getNick(), text);
    agentService.submit(
        new AgentInvocation(liveBotContext, prompt, AgentInvocationMode.MODERATION));
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
    return CONVENTIONAL_BOT_NICK.matcher(nick).matches()
        || engine.currentChannelUsers.stream()
            .anyMatch(user -> user.isBot() && user.getNick().equalsIgnoreCase(nick));
  }

  private static boolean requiresSemanticModeration(String text) {
    return SEMANTIC_MODERATION_SIGNAL.matcher(text).find();
  }
}
