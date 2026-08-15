package org.saturn.app.agent.moderation;

import com.moandjiezana.toml.Toml;
import java.time.Duration;
import java.util.Objects;

public record AgentModerationConfig(
    boolean enabled,
    int messageBurstCount,
    Duration messageBurstWindow,
    int repeatedMessageCount,
    Duration repeatedMessageWindow,
    Duration secondBreachWindow,
    Duration postKickWindow,
    int joinBurstCount,
    Duration joinBurstWindow,
    int sameHashJoinCount,
    Duration sameHashJoinWindow,
    int suspiciousNameJoinCount,
    Duration suspiciousNameJoinWindow,
    Duration actionCooldown) {
  public AgentModerationConfig {
    requirePositive(messageBurstCount, "moderationMessageBurstCount");
    requirePositive(messageBurstWindow, "moderationMessageBurstWindowSeconds");
    requirePositive(repeatedMessageCount, "moderationRepeatedMessageCount");
    requirePositive(repeatedMessageWindow, "moderationRepeatedMessageWindowSeconds");
    requirePositive(secondBreachWindow, "moderationSecondBreachWindowSeconds");
    requirePositive(postKickWindow, "moderationPostKickWindowSeconds");
    requirePositive(joinBurstCount, "moderationJoinBurstCount");
    requirePositive(joinBurstWindow, "moderationJoinBurstWindowSeconds");
    requirePositive(sameHashJoinCount, "moderationSameHashJoinCount");
    requirePositive(sameHashJoinWindow, "moderationSameHashJoinWindowSeconds");
    requirePositive(suspiciousNameJoinCount, "moderationSuspiciousNameJoinCount");
    requirePositive(suspiciousNameJoinWindow, "moderationSuspiciousNameJoinWindowSeconds");
    requirePositive(actionCooldown, "moderationActionCooldownSeconds");
  }

  public static AgentModerationConfig from(Toml root) {
    Toml table = root == null ? null : root.getTable("agent");
    return new AgentModerationConfig(
        readBoolean(table, "moderationEnabled", true),
        readInt(table, "moderationMessageBurstCount", 6),
        readSeconds(table, "moderationMessageBurstWindowSeconds", 5),
        readInt(table, "moderationRepeatedMessageCount", 4),
        readSeconds(table, "moderationRepeatedMessageWindowSeconds", 10),
        readSeconds(table, "moderationSecondBreachWindowSeconds", 30),
        readSeconds(table, "moderationPostKickWindowSeconds", 600),
        readInt(table, "moderationJoinBurstCount", 8),
        readSeconds(table, "moderationJoinBurstWindowSeconds", 10),
        readInt(table, "moderationSameHashJoinCount", 5),
        readSeconds(table, "moderationSameHashJoinWindowSeconds", 20),
        readInt(table, "moderationSuspiciousNameJoinCount", 5),
        readSeconds(table, "moderationSuspiciousNameJoinWindowSeconds", 20),
        readSeconds(table, "moderationActionCooldownSeconds", 30));
  }

  private static int readInt(Toml table, String key, long fallback) {
    long value = readLong(table, key, fallback);
    try {
      return Math.toIntExact(value);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("agent." + key + " is outside integer range", exception);
    }
  }

  private static Duration readSeconds(Toml table, String key, long fallback) {
    return Duration.ofSeconds(readLong(table, key, fallback));
  }

  private static long readLong(Toml table, String key, long fallback) {
    Long value = table == null ? null : table.getLong(key);
    return value == null ? fallback : value;
  }

  private static boolean readBoolean(Toml table, String key, boolean fallback) {
    Boolean value = table == null ? null : table.getBoolean(key);
    return value == null ? fallback : value;
  }

  private static void requirePositive(long value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException("agent." + name + " must be positive");
    }
  }

  private static void requirePositive(Duration value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("agent." + name + " must be positive");
    }
  }
}
