package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moandjiezana.toml.Toml;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentConfigValueReaderTest {
  @Test
  void environmentValueOverridesBlankAwareTomlFallback() {
    Toml root = new Toml().read("[agent]\nlimit = 7\n");

    assertEquals(
        12L,
        AgentConfigValueReader.readLong(
            root.getTable("agent"), Map.of("SATURN_LIMIT", " 12 "), "limit", "SATURN_LIMIT", 3));
    assertEquals(
        7L,
        AgentConfigValueReader.readLong(
            root.getTable("agent"), Map.of("SATURN_LIMIT", "  "), "limit", "SATURN_LIMIT", 3));
  }

  @Test
  void parsesStrictEnvironmentBooleansAndReportsInvalidValues() {
    assertEquals(
        true,
        AgentConfigValueReader.readBoolean(
            null, Map.of("SATURN_ENABLED", "TRUE"), "enabled", "SATURN_ENABLED", false));
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AgentConfigValueReader.readBoolean(
                    null, Map.of("SATURN_ENABLED", "yes"), "enabled", "SATURN_ENABLED", false));
    assertEquals("SATURN_ENABLED must be true or false", exception.getMessage());
    assertEquals(
        false,
        AgentConfigValueReader.readBoolean(
            null, Map.of("SATURN_ENABLED", " false "), "enabled", "SATURN_ENABLED", true));
  }

  @Test
  void rejectsMalformedEnvironmentIntegersAndNonPositiveValues() {
    IllegalArgumentException integerException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AgentConfigValueReader.readLong(
                    null, Map.of("SATURN_LIMIT", "not-a-number"), "limit", "SATURN_LIMIT", 3));
    assertEquals("SATURN_LIMIT must be an integer", integerException.getMessage());

    IllegalArgumentException positiveException =
        assertThrows(
            IllegalArgumentException.class,
            () -> AgentConfigValueReader.requirePositive(0, "limit"));
    assertEquals("agent.limit must be positive", positiveException.getMessage());
  }

  @Test
  void convertsLongToIntWithoutSilentOverflow() {
    assertEquals(4, AgentConfigValueReader.toInt(4L, "limit"));
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> AgentConfigValueReader.toInt((long) Integer.MAX_VALUE + 1, "limit"));
    assertEquals("agent.limit is outside integer range", exception.getMessage());
  }
}
