package org.saturn.app.agent.moderation;

import com.moandjiezana.toml.Toml;
import java.time.Duration;
import java.util.Objects;
import org.saturn.app.agent.config.AgentConfigValueReader;

/** Configures moderation thresholds and actions for agent-managed rooms. */
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
        AgentConfigValueReader.readBoolean(table, "moderationEnabled", true),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(table, "moderationMessageBurstCount", 6),
            "moderationMessageBurstCount"),
        Duration.ofSeconds(
            AgentConfigValueReader.readLong(table, "moderationMessageBurstWindowSeconds", 5)),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(table, "moderationRepeatedMessageCount", 4),
            "moderationRepeatedMessageCount"),
        Duration.ofSeconds(
            AgentConfigValueReader.readLong(table, "moderationRepeatedMessageWindowSeconds", 10)),
        Duration.ofSeconds(
            AgentConfigValueReader.readLong(table, "moderationSecondBreachWindowSeconds", 30)),
        Duration.ofSeconds(
            AgentConfigValueReader.readLong(table, "moderationPostKickWindowSeconds", 600)),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(table, "moderationJoinBurstCount", 8),
            "moderationJoinBurstCount"),
        Duration.ofSeconds(
            AgentConfigValueReader.readLong(table, "moderationJoinBurstWindowSeconds", 10)),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(table, "moderationSameHashJoinCount", 5),
            "moderationSameHashJoinCount"),
        Duration.ofSeconds(
            AgentConfigValueReader.readLong(table, "moderationSameHashJoinWindowSeconds", 20)),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(table, "moderationSuspiciousNameJoinCount", 5),
            "moderationSuspiciousNameJoinCount"),
        Duration.ofSeconds(
            AgentConfigValueReader.readLong(
                table, "moderationSuspiciousNameJoinWindowSeconds", 20)),
        Duration.ofSeconds(
            AgentConfigValueReader.readLong(table, "moderationActionCooldownSeconds", 30)));
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
