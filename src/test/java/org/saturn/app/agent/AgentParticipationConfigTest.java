package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moandjiezana.toml.Toml;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AgentParticipationConfigTest {
  @Test
  void defaultsInvocationsToDirectAndSupportsSilentResults() {
    AgentContext context =
        new AgentContext("lounge", "alice", "trip", "hash", false, java.util.List.of());

    AgentInvocation invocation = new AgentInvocation(context, "hello");

    assertEquals(AgentInvocationMode.DIRECT, invocation.mode());
    assertTrue(invocation.mode().requiresReply());
    assertTrue(AgentResult.reply("reply-id", "hello").shouldReply());
    assertFalse(AgentResult.silent("silent-id").shouldReply());
    assertEquals("", AgentResult.silent("silent-id").content());
  }

  @Test
  void appliesApprovedParticipationDefaults() {
    AgentParticipationConfig actual = AgentParticipationConfig.from(new Toml());

    assertEquals("595754", actual.creatorTrip());
    assertFalse(actual.ambientEnabled());
    assertEquals(8, actual.ambientEveryMessages());
    assertEquals(Duration.ofMinutes(15), actual.quietDuration());
    assertEquals(20, actual.contextMessageLimit());
    assertEquals("[[SATURN_NO_REPLY]]", actual.noReplyMarker());
  }

  @Test
  void readsEveryParticipationSetting() {
    Toml config =
        new Toml()
            .read(
                """
                [agent]
                creatorTrip = "trusted"
                ambientEnabled = false
                ambientEveryMessages = 10
                quietMinutes = 3
                contextMessageLimit = 12
                noReplyMarker = "<silent>"
                """);

    AgentParticipationConfig actual = AgentParticipationConfig.from(config);

    assertEquals("trusted", actual.creatorTrip());
    assertFalse(actual.ambientEnabled());
    assertEquals(10, actual.ambientEveryMessages());
    assertEquals(Duration.ofMinutes(3), actual.quietDuration());
    assertEquals(12, actual.contextMessageLimit());
    assertEquals("<silent>", actual.noReplyMarker());
  }

  @Test
  void rejectsBlankIdentityMarkersAndNonPositiveLimits() {
    Toml blankTrip = new Toml().read("[agent]\ncreatorTrip = \"  \"");
    Toml blankMarker = new Toml().read("[agent]\nnoReplyMarker = \"\"");
    Toml zeroAmbientCadence = new Toml().read("[agent]\nambientEveryMessages = 0");
    Toml zeroQuiet = new Toml().read("[agent]\nquietMinutes = 0");
    Toml zeroContext = new Toml().read("[agent]\ncontextMessageLimit = 0");

    assertThrows(IllegalArgumentException.class, () -> AgentParticipationConfig.from(blankTrip));
    assertThrows(IllegalArgumentException.class, () -> AgentParticipationConfig.from(blankMarker));
    assertThrows(
        IllegalArgumentException.class, () -> AgentParticipationConfig.from(zeroAmbientCadence));
    assertThrows(IllegalArgumentException.class, () -> AgentParticipationConfig.from(zeroQuiet));
    assertThrows(IllegalArgumentException.class, () -> AgentParticipationConfig.from(zeroContext));
  }
}
