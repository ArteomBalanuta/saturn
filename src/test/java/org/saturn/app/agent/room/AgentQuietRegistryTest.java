package org.saturn.app.agent.room;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentUserIdentity;

class AgentQuietRegistryTest {
  @Test
  void suppressesOnlyTheStableUserAndRoomUntilExpiry() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));
    AgentQuietRegistry registry = new AgentQuietRegistry(Duration.ofMinutes(15), clock);
    AgentContext alice = context("lounge", "alice", "trip-a", "hash-a");
    AgentContext renamedAlice = context("lounge", "alice2", "trip-a", "different-hash");
    AgentContext otherRoom = context("programming", "alice", "trip-a", "hash-a");
    AgentContext bob = context("lounge", "bob", "trip-b", "hash-b");

    registry.silence(alice);

    assertTrue(registry.isQuiet(alice));
    assertTrue(registry.isQuiet(renamedAlice));
    assertFalse(registry.isQuiet(otherRoom));
    assertFalse(registry.isQuiet(bob));
    clock.advance(Duration.ofMinutes(15));
    assertFalse(registry.isQuiet(alice));
  }

  @Test
  void recognizesPoliteQuietRequestsWithoutMatchingOrdinaryDiscussion() {
    AgentQuietRegistry registry = new AgentQuietRegistry(Duration.ofMinutes(15), Clock.systemUTC());

    assertTrue(registry.isPoliteQuietRequest("Vaelen, please be quiet", "korin"));
    assertTrue(registry.isPoliteQuietRequest("please be silent", "korin"));
    assertTrue(registry.isPoliteQuietRequest("@korin could you stay out of this chat?", "korin"));
    assertTrue(registry.isPoliteQuietRequest("please don't join my conversation", "korin"));
    assertFalse(registry.isPoliteQuietRequest("people should stay quiet in libraries", "korin"));
    assertFalse(registry.isPoliteQuietRequest("korin shut up", "korin"));
  }

  @Test
  void fallsBackFromTripToHashAndThenNormalizedNick() {
    assertTrue(
        AgentUserIdentity.from(context("room", "Alice", null, "hash-a"))
            .equals(AgentUserIdentity.from(context("room", "alice2", "", "hash-a"))));
    assertTrue(
        AgentUserIdentity.from(context("room", "Alice", null, null))
            .equals(AgentUserIdentity.from(context("room", "ALICE", "", ""))));
  }

  @Test
  void rejectsNonPositiveQuietDurations() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AgentQuietRegistry(Duration.ZERO, Clock.systemUTC()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AgentQuietRegistry(Duration.ofSeconds(-1), Clock.systemUTC()));
  }

  @Test
  void ignoresNullAndBlankQuietRequests() {
    AgentQuietRegistry registry = new AgentQuietRegistry(Duration.ofMinutes(15), Clock.systemUTC());

    assertFalse(registry.isPoliteQuietRequest(null, "korin"));
    assertFalse(registry.isPoliteQuietRequest("  \n  ", "korin"));
  }

  private AgentContext context(String room, String nick, String trip, String hash) {
    return new AgentContext(room, nick, trip, hash, false, List.of());
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
