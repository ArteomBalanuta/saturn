package org.saturn.app.agent;

import com.moandjiezana.toml.Toml;
import java.time.Duration;
import java.util.Objects;

public record AgentParticipationConfig(
    String creatorTrip,
    boolean ambientEnabled,
    int ambientEveryMessages,
    Duration quietDuration,
    int contextMessageLimit,
    String noReplyMarker) {
  private static final String DEFAULT_CREATOR_TRIP = "595754";
  private static final String DEFAULT_NO_REPLY_MARKER = "[[SATURN_NO_REPLY]]";

  public AgentParticipationConfig {
    creatorTrip = requireNotBlank(creatorTrip, "creatorTrip");
    Objects.requireNonNull(quietDuration, "quietDuration");
    noReplyMarker = requireNotBlank(noReplyMarker, "noReplyMarker");
    requirePositive(ambientEveryMessages, "ambientEveryMessages");
    requirePositive(quietDuration, "quietMinutes");
    requirePositive(contextMessageLimit, "contextMessageLimit");
  }

  public static AgentParticipationConfig from(Toml root) {
    Toml table = root == null ? null : root.getTable("agent");
    return new AgentParticipationConfig(
        readString(table, "creatorTrip", DEFAULT_CREATOR_TRIP),
        readBoolean(table, "ambientEnabled", false),
        toInt(readLong(table, "ambientEveryMessages", 8), "ambientEveryMessages"),
        Duration.ofMinutes(readLong(table, "quietMinutes", 15)),
        toInt(readLong(table, "contextMessageLimit", 60), "contextMessageLimit"),
        readString(table, "noReplyMarker", DEFAULT_NO_REPLY_MARKER));
  }

  private static String readString(Toml table, String key, String fallback) {
    String value = table == null ? null : table.getString(key);
    return value == null ? fallback : value;
  }

  private static long readLong(Toml table, String key, long fallback) {
    Long value = table == null ? null : table.getLong(key);
    return value == null ? fallback : value;
  }

  private static boolean readBoolean(Toml table, String key, boolean fallback) {
    Boolean value = table == null ? null : table.getBoolean(key);
    return value == null ? fallback : value;
  }

  private static int toInt(long value, String name) {
    try {
      return Math.toIntExact(value);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("agent." + name + " is outside integer range", exception);
    }
  }

  private static String requireNotBlank(String value, String name) {
    Objects.requireNonNull(value, name);
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("agent." + name + " must not be blank");
    }
    return normalized;
  }

  private static void requirePositive(long value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException("agent." + name + " must be positive");
    }
  }

  private static void requirePositive(Duration value, String name) {
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("agent." + name + " must be positive");
    }
  }
}
