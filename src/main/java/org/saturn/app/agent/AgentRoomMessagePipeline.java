package org.saturn.app.agent;

import java.util.List;
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
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.AgentService;

/** Ordered room-event chain for deterministic moderation, mentions, silence, and ambient turns. */
@Slf4j
final class AgentRoomMessagePipeline {
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
  private final List<Handler> handlers;

  AgentRoomMessagePipeline(
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
    this.handlers =
        List.of(
            this::monitorModeration,
            this::filterIneligible,
            this::prepareInvocation,
            this::handleQuietRequest,
            this::handleMention,
            this::handleSemanticModeration,
            this::handleAmbientParticipation);
  }

  AgentRoomAutomation.Outcome onMessage(ChatMessage message) {
    Turn turn = new Turn(message);
    for (Handler handler : handlers) {
      Decision decision = handler.handle(turn);
      if (decision == Decision.PASS) return AgentRoomAutomation.Outcome.PASS;
      if (decision == Decision.CLAIMED) return AgentRoomAutomation.Outcome.CLAIMED;
    }
    return AgentRoomAutomation.Outcome.PASS;
  }

  void onJoin(User user) {
    execute(moderationMonitor.onJoin(user));
  }

  private Decision monitorModeration(Turn turn) {
    execute(moderationMonitor.onMessage(turn.message));
    return Decision.CONTINUE;
  }

  private Decision filterIneligible(Turn turn) {
    turn.text = turn.message.getText().strip();
    return turn.text.isEmpty()
            || turn.message.isWhisper()
            || engine.nick.equalsIgnoreCase(turn.message.getNick())
            || isBotAuthor(turn.message.getNick())
            || turn.text.startsWith(engine.prefix)
        ? Decision.PASS
        : Decision.CONTINUE;
  }

  private Decision prepareInvocation(Turn turn) {
    turn.mentionPrompt = mentionParser.parse(turn.text, engine.nick);
    AgentInvocationMode mode =
        turn.mentionPrompt.isPresent() ? AgentInvocationMode.MENTION : AgentInvocationMode.AMBIENT;
    turn.invocation =
        invocationFactory.create(engine, turn.message, turn.mentionPrompt.orElse(turn.text), mode);
    return Decision.CONTINUE;
  }

  private Decision handleQuietRequest(Turn turn) {
    if (!quietRegistry.isPoliteQuietRequest(turn.text, engine.nick)) return Decision.CONTINUE;
    quietRegistry.silence(turn.invocation.context());
    return Decision.PASS;
  }

  private Decision handleMention(Turn turn) {
    if (turn.mentionPrompt.isEmpty()) return Decision.CONTINUE;
    agentService.submit(turn.invocation);
    return Decision.CLAIMED;
  }

  private Decision handleSemanticModeration(Turn turn) {
    if (botModerationContext == null
        || !semanticModerationCandidate.test(turn.message)
        || !SEMANTIC_MODERATION_SIGNAL.matcher(turn.text).find()) return Decision.CONTINUE;
    AgentContext liveBotContext =
        new AgentContext(
            engine.channel,
            engine.nick,
            botModerationContext.trip(),
            botModerationContext.hash(),
            false,
            engine.currentChannelUsers.stream().map(User::getNick).toList(),
            botModerationContext.capabilities(),
            turn.message.getNick());
    String prompt =
        "Review this public chat message for severe abuse. Author nick: %s. Message: %s"
            .formatted(turn.message.getNick(), turn.text);
    agentService.submit(
        new AgentInvocation(liveBotContext, prompt, AgentInvocationMode.MODERATION));
    return Decision.CONTINUE;
  }

  private Decision handleAmbientParticipation(Turn turn) {
    if (!config.ambientEnabled() || quietRegistry.isQuiet(turn.invocation.context())) {
      return Decision.PASS;
    }
    if (Math.floorMod(eligibleAmbientMessages.incrementAndGet(), config.ambientEveryMessages())
        != 0) return Decision.PASS;
    agentService.submit(turn.invocation);
    return Decision.PASS;
  }

  private void execute(List<ModerationDecision> decisions) {
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

  @FunctionalInterface
  private interface Handler {
    Decision handle(Turn turn);
  }

  private enum Decision {
    CONTINUE,
    PASS,
    CLAIMED
  }

  private static final class Turn {
    private final ChatMessage message;
    private String text;
    private Optional<String> mentionPrompt = Optional.empty();
    private AgentInvocation invocation;

    private Turn(ChatMessage message) {
      this.message = Objects.requireNonNull(message, "message");
    }
  }
}
