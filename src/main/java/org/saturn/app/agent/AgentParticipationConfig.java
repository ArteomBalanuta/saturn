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
        AgentConfigValueReader.readString(table, "creatorTrip", DEFAULT_CREATOR_TRIP),
        AgentConfigValueReader.readBoolean(table, "ambientEnabled", false),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(table, "ambientEveryMessages", 8),
            "ambientEveryMessages"),
        Duration.ofMinutes(AgentConfigValueReader.readLong(table, "quietMinutes", 15)),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(table, "contextMessageLimit", 60),
            "contextMessageLimit"),
        AgentConfigValueReader.readString(table, "noReplyMarker", DEFAULT_NO_REPLY_MARKER));
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
