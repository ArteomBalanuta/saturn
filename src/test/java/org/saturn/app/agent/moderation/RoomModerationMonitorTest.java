package org.saturn.app.agent.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moandjiezana.toml.Toml;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

class RoomModerationMonitorTest {
  @Test
  void escalatesBurstRepeatedSpamAndPostKickReoffenceWithoutPermanentBan() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor monitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);
    List<ModerationDecision> decisions = new ArrayList<>();

    for (int index = 0; index < 6; index++) {
      decisions.addAll(monitor.onMessage(message("spammer", "trip-s", "hash-s", "burst-" + index)));
    }
    clock.advance(Duration.ofSeconds(1));
    repeat(monitor, decisions, "same message", 4);
    clock.advance(Duration.ofSeconds(1));
    repeat(monitor, decisions, "same message", 4);
    clock.advance(Duration.ofSeconds(1));
    repeat(monitor, decisions, "same message", 4);

    assertEquals(
        List.of(
            ModerationAction.WARN,
            ModerationAction.MUTE,
            ModerationAction.KICK,
            ModerationAction.SHADOWBAN),
        decisions.stream().map(ModerationDecision::action).toList());
    assertTrue(
        Arrays.stream(ModerationAction.values()).noneMatch(action -> action.name().equals("BAN")));
  }

  @Test
  void enablesCaptchaForJoinBurstAndSameHashNickVariants() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor burstMonitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);
    List<ModerationDecision> burst = new ArrayList<>();
    for (int index = 0; index < 8; index++) {
      burst.addAll(burstMonitor.onJoin(user("guest" + index, "trip-" + index, "hash-" + index)));
    }

    clock.advance(Duration.ofMinutes(1));
    RoomModerationMonitor hashMonitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);
    List<ModerationDecision> variants = new ArrayList<>();
    for (int index = 0; index < 5; index++) {
      variants.addAll(hashMonitor.onJoin(user("variant" + index, null, "shared-hash")));
    }

    assertEquals(List.of(ModerationAction.CAPTCHA_ON), actions(burst));
    assertEquals(List.of(ModerationAction.CAPTCHA_ON), actions(variants));
  }

  @Test
  void suspiciousNameClusterEnablesCaptchaButNeverTargetsAUser() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor monitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);
    List<ModerationDecision> decisions = new ArrayList<>();

    for (int index = 1; index <= 5; index++) {
      decisions.addAll(monitor.onJoin(user("raid00" + index, null, "hash-" + index)));
    }

    assertEquals(List.of(ModerationAction.CAPTCHA_ON), actions(decisions));
    assertTrue(decisions.getFirst().target().isEmpty());
    assertTrue(
        decisions.stream().noneMatch(decision -> decision.action() == ModerationAction.SHADOWBAN));
  }

  @Test
  void excludesProtectedUsersBeforeRecordingDetectionState() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor monitor =
        new RoomModerationMonitor(
            AgentModerationConfig.from(new Toml()),
            clock,
            message -> "admin-trip".equals(message.getTrip()),
            user -> "admin-trip".equals(user.getTrip()));
    List<ModerationDecision> decisions = new ArrayList<>();

    for (int index = 0; index < 20; index++) {
      decisions.addAll(
          monitor.onMessage(message("admin", "admin-trip", "admin-hash", "message-" + index)));
      decisions.addAll(monitor.onJoin(user("admin" + index, "admin-trip", "admin-hash")));
    }

    assertTrue(decisions.isEmpty());
  }

  @Test
  void suppressesDuplicateCaptchaActionsWithinCooldown() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor monitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);
    List<ModerationDecision> decisions = new ArrayList<>();

    for (int wave = 0; wave < 2; wave++) {
      for (int index = 0; index < 5; index++) {
        decisions.addAll(
            monitor.onJoin(user("wave" + wave + "raid00" + index, null, "shared-" + wave)));
      }
      clock.advance(Duration.ofSeconds(1));
    }

    assertEquals(
        1, decisions.stream().filter(d -> d.action() == ModerationAction.CAPTCHA_ON).count());
  }

  @Test
  void allowsCaptchaAgainAfterTheActionCooldownExpires() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor monitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);
    List<ModerationDecision> decisions = new ArrayList<>();

    for (int wave = 0; wave < 2; wave++) {
      for (int index = 0; index < 5; index++) {
        decisions.addAll(
            monitor.onJoin(user("cooldown" + wave + "raid00" + index, null, "hash-" + wave)));
      }
      clock.advance(Duration.ofSeconds(31));
    }

    assertEquals(
        2, decisions.stream().filter(d -> d.action() == ModerationAction.CAPTCHA_ON).count());
  }

  @Test
  void startsASecondBreachAtWarningAfterTheWarningWindowExpires() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor monitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);
    List<ModerationDecision> decisions = new ArrayList<>();

    for (int index = 0; index < 6; index++) {
      decisions.addAll(monitor.onMessage(message("spammer", "trip-s", "hash-s", "burst-" + index)));
    }
    clock.advance(Duration.ofSeconds(31));
    for (int index = 0; index < 6; index++) {
      decisions.addAll(monitor.onMessage(message("spammer", "trip-s", "hash-s", "again-" + index)));
    }

    assertEquals(
        List.of(ModerationAction.WARN, ModerationAction.WARN),
        decisions.stream().map(ModerationDecision::action).toList());
  }

  @Test
  void ignoresEmptyMessagesAndDisabledMonitoring() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor monitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);

    assertTrue(monitor.onMessage(message("alice", "trip-a", "hash-a", " \\n")).isEmpty());
    assertTrue(
        RoomModerationMonitor.disabled()
            .onMessage(message("alice", "trip-a", "hash-a", "message"))
            .isEmpty());
    assertTrue(
        RoomModerationMonitor.disabled().onJoin(user("alice", "trip-a", "hash-a")).isEmpty());
  }

  @Test
  void ignoresWhispersAndPrunesExpiredMessageHistory() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor monitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);
    ChatMessage whisper = message("alice", "trip-a", "hash-a", "message");
    whisper.setWhisper(true);

    assertTrue(monitor.onMessage(whisper).isEmpty());
    for (int index = 0; index < 5; index++) {
      assertTrue(
          monitor.onMessage(message("alice", "trip-a", "hash-a", "burst" + index)).isEmpty());
    }
    clock.advance(Duration.ofMinutes(11));
    assertTrue(monitor.onMessage(message("alice", "trip-a", "hash-a", "new message")).isEmpty());
  }

  @Test
  void acceptsJoinsWithoutHashesAndShortNameClusters() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor monitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);

    assertTrue(monitor.onJoin(user("ab1", "trip-a", null)).isEmpty());
    assertTrue(monitor.onJoin(user("ab2", "trip-b", "   ")).isEmpty());
  }

  @Test
  void shadowbansRepeatedSameHashRaidsAfterTheSecondVariantWave() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor monitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);
    List<ModerationDecision> decisions = new ArrayList<>();

    for (int wave = 0; wave < 2; wave++) {
      for (int index = 0; index < 5; index++) {
        decisions.addAll(monitor.onJoin(user("variant" + wave + index, null, "shared-hash")));
      }
      clock.advance(Duration.ofSeconds(1));
    }

    assertTrue(
        decisions.stream().anyMatch(decision -> decision.action() == ModerationAction.SHADOWBAN));
  }

  @Test
  void doesNotShadowbanWhenThePreviousSameHashSignalHasExpired() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    RoomModerationMonitor monitor =
        new RoomModerationMonitor(AgentModerationConfig.from(new Toml()), clock);
    List<ModerationDecision> decisions = new ArrayList<>();

    for (int wave = 0; wave < 2; wave++) {
      for (int index = 0; index < 5; index++) {
        decisions.addAll(monitor.onJoin(user("expired" + wave + index, null, "shared-hash")));
      }
      clock.advance(Duration.ofSeconds(601));
    }

    assertEquals(
        2,
        decisions.stream()
            .filter(decision -> decision.action() == ModerationAction.CAPTCHA_ON)
            .count());
    assertTrue(
        decisions.stream().noneMatch(decision -> decision.action() == ModerationAction.SHADOWBAN));
  }

  private void repeat(
      RoomModerationMonitor monitor, List<ModerationDecision> decisions, String text, int count) {
    for (int index = 0; index < count; index++) {
      decisions.addAll(monitor.onMessage(message("spammer", "trip-s", "hash-s", text)));
    }
  }

  private List<ModerationAction> actions(List<ModerationDecision> decisions) {
    return decisions.stream().map(ModerationDecision::action).toList();
  }

  private ChatMessage message(String nick, String trip, String hash, String text) {
    return new ChatMessage(null, nick, trip, hash, null, text);
  }

  private User user(String nick, String trip, String hash) {
    return new User("programming", false, nick, trip, "", hash, 0, 0, false);
  }

  private static final class MutableClock extends Clock {
    private final AtomicReference<Instant> instant;

    private MutableClock(Instant instant) {
      this.instant = new AtomicReference<>(instant);
    }

    private void advance(Duration duration) {
      instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant.get();
    }
  }
}
