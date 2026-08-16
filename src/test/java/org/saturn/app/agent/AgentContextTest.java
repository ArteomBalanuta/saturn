package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentContextTest {
  @Test
  void copiesMutableCollectionsAndExposesCapabilityMembership() {
    List<String> users = new ArrayList<>(List.of("alice"));
    Set<AgentCapability> capabilities = EnumSet.of(AgentCapability.ADMIN_COMMANDS);
    AgentContext context =
        new AgentContext("room", "alice", "trip", "hash", false, users, capabilities);

    users.add("bob");
    capabilities.clear();

    assertEquals(List.of("alice"), context.roomUsers());
    assertTrue(context.hasCapability(AgentCapability.ADMIN_COMMANDS));
    assertThrows(UnsupportedOperationException.class, () -> context.roomUsers().add("bob"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> context.capabilities().remove(AgentCapability.ADMIN_COMMANDS));
  }

  @Test
  void derivesPublicAndWhisperMemoryKeysUsingStableIdentityPrecedence() {
    assertEquals(
        "4:room|public",
        new AgentContext("room", "alice", "trip", "hash", false, List.of()).memoryKey());
    assertEquals(
        "4:room|whisper|trip:trip",
        new AgentContext("room", "alice", "trip", "hash", true, List.of()).memoryKey());
    assertEquals(
        "4:room|whisper|hash:hash",
        new AgentContext("room", "alice", " ", "hash", true, List.of()).memoryKey());
    assertEquals(
        "4:room|whisper|nick:alice",
        new AgentContext("room", "alice", null, " ", true, List.of()).memoryKey());
  }

  @Test
  void rejectsMissingRequiredContextCollectionsAndIdentity() {
    assertThrows(
        NullPointerException.class,
        () -> new AgentContext(null, "alice", null, null, false, List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new AgentContext("room", null, null, null, false, List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new AgentContext("room", "alice", null, null, false, null));
    assertThrows(
        NullPointerException.class,
        () ->
            new AgentContext(
                "room", "alice", null, null, false, List.of(), (Set<AgentCapability>) null));
  }

  @Test
  void reportsAbsentCapabilitiesWithoutChangingContext() {
    AgentContext context =
        new AgentContext("room", "alice", null, null, false, List.of(), Set.of());

    assertFalse(context.hasCapability(AgentCapability.ADMIN_COMMANDS));
    assertEquals("4:room|public", context.memoryKey());
  }
}
