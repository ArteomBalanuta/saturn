package org.saturn.app.agent.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moandjiezana.toml.Toml;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AgentModerationConfigTest {
  @Test
  void appliesApprovedModerationDefaults() {
    AgentModerationConfig actual = AgentModerationConfig.from(new Toml());

    assertTrue(actual.enabled());
    assertEquals(6, actual.messageBurstCount());
    assertEquals(Duration.ofSeconds(5), actual.messageBurstWindow());
    assertEquals(4, actual.repeatedMessageCount());
    assertEquals(Duration.ofSeconds(10), actual.repeatedMessageWindow());
    assertEquals(Duration.ofSeconds(30), actual.secondBreachWindow());
    assertEquals(Duration.ofMinutes(10), actual.postKickWindow());
    assertEquals(8, actual.joinBurstCount());
    assertEquals(Duration.ofSeconds(10), actual.joinBurstWindow());
    assertEquals(5, actual.sameHashJoinCount());
    assertEquals(Duration.ofSeconds(20), actual.sameHashJoinWindow());
    assertEquals(5, actual.suspiciousNameJoinCount());
    assertEquals(Duration.ofSeconds(20), actual.suspiciousNameJoinWindow());
    assertEquals(Duration.ofSeconds(30), actual.actionCooldown());
  }

  @Test
  void readsEveryModerationSetting() {
    Toml config =
        new Toml()
            .read(
                """
                [agent]
                moderationEnabled = false
                moderationMessageBurstCount = 9
                moderationMessageBurstWindowSeconds = 7
                moderationRepeatedMessageCount = 5
                moderationRepeatedMessageWindowSeconds = 12
                moderationSecondBreachWindowSeconds = 45
                moderationPostKickWindowSeconds = 900
                moderationJoinBurstCount = 11
                moderationJoinBurstWindowSeconds = 13
                moderationSameHashJoinCount = 6
                moderationSameHashJoinWindowSeconds = 24
                moderationSuspiciousNameJoinCount = 7
                moderationSuspiciousNameJoinWindowSeconds = 25
                moderationActionCooldownSeconds = 40
                """);

    AgentModerationConfig actual = AgentModerationConfig.from(config);

    assertFalse(actual.enabled());
    assertEquals(9, actual.messageBurstCount());
    assertEquals(Duration.ofSeconds(7), actual.messageBurstWindow());
    assertEquals(5, actual.repeatedMessageCount());
    assertEquals(Duration.ofSeconds(12), actual.repeatedMessageWindow());
    assertEquals(Duration.ofSeconds(45), actual.secondBreachWindow());
    assertEquals(Duration.ofSeconds(900), actual.postKickWindow());
    assertEquals(11, actual.joinBurstCount());
    assertEquals(Duration.ofSeconds(13), actual.joinBurstWindow());
    assertEquals(6, actual.sameHashJoinCount());
    assertEquals(Duration.ofSeconds(24), actual.sameHashJoinWindow());
    assertEquals(7, actual.suspiciousNameJoinCount());
    assertEquals(Duration.ofSeconds(25), actual.suspiciousNameJoinWindow());
    assertEquals(Duration.ofSeconds(40), actual.actionCooldown());
  }

  @Test
  void rejectsNonPositiveThresholdsAndWindows() {
    Toml zeroCount = new Toml().read("[agent]\nmoderationMessageBurstCount = 0");
    Toml zeroWindow = new Toml().read("[agent]\nmoderationSuspiciousNameJoinWindowSeconds = 0");

    assertThrows(IllegalArgumentException.class, () -> AgentModerationConfig.from(zeroCount));
    assertThrows(IllegalArgumentException.class, () -> AgentModerationConfig.from(zeroWindow));
  }

  @Test
  void rejectsCountsOutsideIntegerRange() {
    Toml config = new Toml().read("[agent]\nmoderationSameHashJoinCount = 2147483648");

    assertThrows(IllegalArgumentException.class, () -> AgentModerationConfig.from(config));
  }
}
