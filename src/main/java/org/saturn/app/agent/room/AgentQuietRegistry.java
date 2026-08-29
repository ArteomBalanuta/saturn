package org.saturn.app.agent.room;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentUserIdentity;

/** Tracks room and user identities that are temporarily quiet for agent participation. */
public final class AgentQuietRegistry {
  private static final Pattern POLITE_LANGUAGE =
      Pattern.compile("\\b(?:please|kindly|could you|would you|can you)\\b");
  private static final Pattern QUIET_INTENT =
      Pattern.compile(
          "\\b(?:be (?:quiet|silent)|stay (?:quiet|silent)|remain silent|keep quiet|stop talking|do"
              + " not join|don'?t join|stay out|leave (?:me|us) alone|do not interrupt|don'?t"
              + " interrupt)\\b");

  private final Duration quietDuration;
  private final Clock clock;
  private final ConcurrentMap<QuietKey, Instant> quietUntil = new ConcurrentHashMap<>();

  /**
   * Implements the {@code AgentQuietRegistry} operation for this agent component.
   *
   * @param quietDuration input argument used by this operation
   * @param clock input argument used by this operation
   */
  public AgentQuietRegistry(Duration quietDuration, Clock clock) {
    this.quietDuration = Objects.requireNonNull(quietDuration, "quietDuration");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (quietDuration.isZero() || quietDuration.isNegative()) {
      throw new IllegalArgumentException("quietDuration must be positive");
    }
  }

  /**
   * Implements the {@code silence} operation for this agent component.
   *
   * @param context input argument used by this operation
   */
  public void silence(AgentContext context) {
    quietUntil.put(key(context), clock.instant().plus(quietDuration));
  }

  /**
   * Implements the {@code isQuiet} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @return the operation result
   */
  public boolean isQuiet(AgentContext context) {
    QuietKey key = key(context);
    Instant expiresAt = quietUntil.get(key);
    if (expiresAt == null) {
      return false;
    }
    if (expiresAt.isAfter(clock.instant())) {
      return true;
    }
    quietUntil.remove(key, expiresAt);
    return false;
  }

  /**
   * Implements the {@code isPoliteQuietRequest} operation for this agent component.
   *
   * @param text input argument used by this operation
   * @param botNick input argument used by this operation
   * @return the operation result
   */
  public boolean isPoliteQuietRequest(String text, String botNick) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String normalized = text.toLowerCase(Locale.ROOT).replace('’', '\'');
    return POLITE_LANGUAGE.matcher(normalized).find() && QUIET_INTENT.matcher(normalized).find();
  }

  /**
   * Implements the {@code key} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @return the operation result
   */
  private static QuietKey key(AgentContext context) {
    return new QuietKey(
        context.room().strip().toLowerCase(Locale.ROOT), AgentUserIdentity.from(context));
  }

  /** Carries the quiet key value used by the enclosing agent component. */
  /** Carries the quiet key value used by the enclosing agent component. */
  private record QuietKey(String room, AgentUserIdentity identity) {}
}
