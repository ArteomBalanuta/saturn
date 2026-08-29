package org.saturn.app.agent.moderation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.saturn.app.agent.api.AgentUserIdentity;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

/** Monitors room events and applies configured moderation decisions. */
public final class RoomModerationMonitor {
  private final AgentModerationConfig config;
  private final Clock clock;
  private final Predicate<ChatMessage> protectedMessage;
  private final Predicate<User> protectedJoin;
  private final Map<AgentUserIdentity, Deque<TimedMessage>> messages = new HashMap<>();
  private final Map<AgentUserIdentity, OffenceState> offences = new HashMap<>();
  private final Deque<TimedJoin> roomJoins = new ArrayDeque<>();
  private final Map<String, Deque<TimedJoin>> hashJoins = new HashMap<>();
  private final Map<String, Deque<TimedJoin>> nameJoins = new HashMap<>();
  private final Map<String, Instant> sameHashSignals = new HashMap<>();
  private final Map<ActionKey, Instant> actions = new HashMap<>();

  /**
   * Implements the {@code RoomModerationMonitor} operation for this agent component.
   *
   * @param config input argument used by this operation
   * @param clock input argument used by this operation
   */
  public RoomModerationMonitor(AgentModerationConfig config, Clock clock) {
    this(config, clock, ignored -> false, ignored -> false);
  }

  /**
   * Implements the {@code RoomModerationMonitor} operation for this agent component.
   *
   * @param config input argument used by this operation
   * @param clock input argument used by this operation
   * @param protectedMessage input argument used by this operation
   * @param protectedJoin input argument used by this operation
   */
  public RoomModerationMonitor(
      AgentModerationConfig config,
      Clock clock,
      Predicate<ChatMessage> protectedMessage,
      Predicate<User> protectedJoin) {
    this.config = Objects.requireNonNull(config, "config");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.protectedMessage = Objects.requireNonNull(protectedMessage, "protectedMessage");
    this.protectedJoin = Objects.requireNonNull(protectedJoin, "protectedJoin");
  }

  /**
   * Implements the {@code disabled} operation for this agent component.
   *
   * @return the operation result
   */
  public static RoomModerationMonitor disabled() {
    return new RoomModerationMonitor(
        new AgentModerationConfig(
            false,
            6,
            Duration.ofSeconds(5),
            4,
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofMinutes(10),
            8,
            Duration.ofSeconds(10),
            5,
            Duration.ofSeconds(20),
            5,
            Duration.ofSeconds(20),
            Duration.ofSeconds(30)),
        Clock.systemUTC());
  }

  /**
   * Implements the {@code onMessage} operation for this agent component.
   *
   * @param message input argument used by this operation
   * @return the operation result
   */
  public synchronized List<ModerationDecision> onMessage(ChatMessage message) {
    if (!config.enabled() || message.isWhisper() || protectedMessage.test(message)) {
      return List.of();
    }
    Instant now = clock.instant();
    AgentUserIdentity identity = AgentUserIdentity.from(message);
    String normalized = normalizeMessage(message.getText());
    if (normalized.isEmpty()) {
      return List.of();
    }

    Deque<TimedMessage> recent = messages.computeIfAbsent(identity, ignored -> new ArrayDeque<>());
    recent.addLast(new TimedMessage(now, normalized));
    prune(recent, now.minus(max(config.messageBurstWindow(), config.repeatedMessageWindow())));
    long burstCount = countSince(recent, now.minus(config.messageBurstWindow()));
    long repeatedCount =
        recent.stream()
            .filter(event -> !event.at().isBefore(now.minus(config.repeatedMessageWindow())))
            .filter(event -> event.normalized().equals(normalized))
            .count();
    boolean repeated = repeatedCount >= config.repeatedMessageCount();
    boolean burst = burstCount >= config.messageBurstCount();
    if (!repeated && !burst) {
      return List.of();
    }

    recent.clear();
    return escalate(identity, message.getNick(), repeated, now).map(List::of).orElseGet(List::of);
  }

  /**
   * Implements the {@code onJoin} operation for this agent component.
   *
   * @param user input argument used by this operation
   * @return the operation result
   */
  public synchronized List<ModerationDecision> onJoin(User user) {
    if (!config.enabled() || protectedJoin.test(user)) {
      return List.of();
    }
    Instant now = clock.instant();
    TimedJoin join = new TimedJoin(now, user.getNick());
    List<ModerationDecision> decisions = new ArrayList<>();

    roomJoins.addLast(join);
    prune(roomJoins, now.minus(config.joinBurstWindow()));
    if (roomJoins.size() >= config.joinBurstCount()) {
      addCaptcha(decisions, "join burst", now);
      roomJoins.clear();
    }

    if (user.getHash() != null && !user.getHash().isBlank()) {
      String hash = user.getHash().strip().toLowerCase(Locale.ROOT);
      Deque<TimedJoin> variants = hashJoins.computeIfAbsent(hash, ignored -> new ArrayDeque<>());
      variants.addLast(join);
      prune(variants, now.minus(config.sameHashJoinWindow()));
      if (distinctNicks(variants) >= config.sameHashJoinCount()) {
        addCaptcha(decisions, "same-hash nick variants", now);
        Instant previousSignal = sameHashSignals.put(hash, now);
        if (previousSignal != null
            && within(previousSignal, now, config.postKickWindow())
            && allow(ModerationAction.SHADOWBAN, AgentUserIdentity.from(user).value(), now)) {
          decisions.add(
              ModerationDecision.targeted(
                  ModerationAction.SHADOWBAN, user.getNick(), "repeated same-hash raid"));
        }
        variants.clear();
      }
    }

    String nameCluster = normalizeNameCluster(user.getNick());
    if (nameCluster.length() >= 3) {
      Deque<TimedJoin> similar =
          nameJoins.computeIfAbsent(nameCluster, ignored -> new ArrayDeque<>());
      similar.addLast(join);
      prune(similar, now.minus(config.suspiciousNameJoinWindow()));
      if (distinctNicks(similar) >= config.suspiciousNameJoinCount()) {
        addCaptcha(decisions, "suspicious name cluster", now);
        similar.clear();
      }
    }

    return List.copyOf(decisions);
  }

  /**
   * Implements the {@code escalate} operation for this agent component.
   *
   * @param identity input argument used by this operation
   * @param nick input argument used by this operation
   * @param repeated input argument used by this operation
   * @param now input argument used by this operation
   * @return the operation result
   */
  private java.util.Optional<ModerationDecision> escalate(
      AgentUserIdentity identity, String nick, boolean repeated, Instant now) {
    OffenceState previous = offences.get(identity);
    ModerationAction action;
    if (previous != null && previous.stage() == OffenceStage.SHADOWBANNED) {
      return java.util.Optional.empty();
    } else if (previous != null
        && previous.stage() == OffenceStage.KICKED
        && within(previous.at(), now, config.postKickWindow())) {
      action = ModerationAction.SHADOWBAN;
    } else if (previous != null
        && previous.stage() == OffenceStage.MUTED
        && within(previous.at(), now, config.secondBreachWindow())) {
      action = ModerationAction.KICK;
    } else if (previous != null
        && previous.stage() == OffenceStage.WARNED
        && within(previous.at(), now, config.secondBreachWindow())) {
      action = ModerationAction.MUTE;
    } else {
      action = repeated ? ModerationAction.MUTE : ModerationAction.WARN;
    }

    if (!allow(action, identity.value(), now)) {
      return java.util.Optional.empty();
    }
    offences.put(identity, new OffenceState(stage(action), now));
    String reason = repeated ? "repeated message spam" : "message burst";
    return java.util.Optional.of(ModerationDecision.targeted(action, nick, reason));
  }

  /**
   * Implements the {@code addCaptcha} operation for this agent component.
   *
   * @param decisions input argument used by this operation
   * @param reason input argument used by this operation
   * @param now input argument used by this operation
   */
  private void addCaptcha(List<ModerationDecision> decisions, String reason, Instant now) {
    if (allow(ModerationAction.CAPTCHA_ON, "room", now)) {
      decisions.add(ModerationDecision.room(ModerationAction.CAPTCHA_ON, reason));
    }
  }

  /**
   * Implements the {@code allow} operation for this agent component.
   *
   * @param action input argument used by this operation
   * @param target input argument used by this operation
   * @param now input argument used by this operation
   * @return the operation result
   */
  private boolean allow(ModerationAction action, String target, Instant now) {
    ActionKey key = new ActionKey(action, target);
    Instant previous = actions.get(key);
    if (previous != null && within(previous, now, config.actionCooldown())) {
      return false;
    }
    actions.put(key, now);
    return true;
  }

  /**
   * Implements the {@code stage} operation for this agent component.
   *
   * @param action input argument used by this operation
   * @return the operation result
   */
  private static OffenceStage stage(ModerationAction action) {
    return switch (action) {
      case WARN -> OffenceStage.WARNED;
      case MUTE -> OffenceStage.MUTED;
      case KICK -> OffenceStage.KICKED;
      case SHADOWBAN -> OffenceStage.SHADOWBANNED;
      case CAPTCHA_ON -> throw new IllegalArgumentException("Captcha is not a user offence stage");
    };
  }

  /**
   * Implements the {@code normalizeMessage} operation for this agent component.
   *
   * @param text input argument used by this operation
   * @return the operation result
   */
  private static String normalizeMessage(String text) {
    return text.replace("\\n", " ")
        .replace('\n', ' ')
        .strip()
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ");
  }

  /**
   * Implements the {@code normalizeNameCluster} operation for this agent component.
   *
   * @param nick input argument used by this operation
   * @return the operation result
   */
  private static String normalizeNameCluster(String nick) {
    return nick.strip()
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]", "")
        .replaceFirst("\\d+$", "");
  }

  /**
   * Implements the {@code countSince} operation for this agent component.
   *
   * @param events input argument used by this operation
   * @param cutoff input argument used by this operation
   * @return the operation result
   */
  private static long countSince(Deque<TimedMessage> events, Instant cutoff) {
    return events.stream().filter(event -> !event.at().isBefore(cutoff)).count();
  }

  /**
   * Implements the {@code distinctNicks} operation for this agent component.
   *
   * @param events input argument used by this operation
   * @return the operation result
   */
  private static long distinctNicks(Deque<TimedJoin> events) {
    return events.stream().map(event -> event.nick().toLowerCase(Locale.ROOT)).distinct().count();
  }

  /**
   * Implements the {@code prune} operation for this agent component.
   *
   * @param events input argument used by this operation
   * @param cutoff input argument used by this operation
   */
  private static <T extends TimedEvent> void prune(Deque<T> events, Instant cutoff) {
    while (!events.isEmpty() && events.getFirst().at().isBefore(cutoff)) {
      events.removeFirst();
    }
  }

  /**
   * Implements the {@code within} operation for this agent component.
   *
   * @param previous input argument used by this operation
   * @param now input argument used by this operation
   * @param window input argument used by this operation
   * @return the operation result
   */
  private static boolean within(Instant previous, Instant now, Duration window) {
    Duration elapsed = Duration.between(previous, now);
    return !elapsed.isNegative() && elapsed.compareTo(window) <= 0;
  }

  /**
   * Implements the {@code max} operation for this agent component.
   *
   * @param first input argument used by this operation
   * @param second input argument used by this operation
   * @return the operation result
   */
  private static Duration max(Duration first, Duration second) {
    return first.compareTo(second) >= 0 ? first : second;
  }

  /** Defines the operation used to timed event. */
  /** Defines the operation used to timed event. */
  private interface TimedEvent {
    Instant at();
  }

  /** Carries the timed message value used by the enclosing agent component. */
  /** Carries the timed message value used by the enclosing agent component. */
  private record TimedMessage(Instant at, String normalized) implements TimedEvent {}

  /** Carries the timed join value used by the enclosing agent component. */
  /** Carries the timed join value used by the enclosing agent component. */
  private record TimedJoin(Instant at, String nick) implements TimedEvent {}

  /** Carries the action key value used by the enclosing agent component. */
  /** Carries the action key value used by the enclosing agent component. */
  private record ActionKey(ModerationAction action, String target) {}

  /** Carries the offence state value used by the enclosing agent component. */
  /** Carries the offence state value used by the enclosing agent component. */
  private record OffenceState(OffenceStage stage, Instant at) {}

  /** Enumerates the possible offence stage states used by the enclosing agent component. */
  /** Enumerates the possible offence stage states used by the enclosing agent component. */
  private enum OffenceStage {
    WARNED,
    MUTED,
    KICKED,
    SHADOWBANNED
  }
}
