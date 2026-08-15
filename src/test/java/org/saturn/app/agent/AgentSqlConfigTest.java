package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moandjiezana.toml.Toml;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentSqlConfigTest {
  @Test
  void appliesConservativeDefaults() {
    AgentSqlConfig actual = AgentSqlConfig.from(new Toml());

    assertTrue(actual.enabled());
    assertEquals(4_000, actual.maxSqlChars());
    assertEquals(50, actual.maxRows());
    assertEquals(32, actual.maxColumns());
    assertEquals(2_000, actual.maxCellChars());
    assertEquals(32_000, actual.maxResultChars());
    assertEquals(Duration.ofSeconds(1), actual.timeout());
  }

  @Test
  void readsEveryDynamicSqlSetting() {
    Toml config =
        new Toml()
            .read(
                """
                [agent]
                dynamicSqlEnabled = false
                dynamicSqlMaxSqlChars = 8000
                dynamicSqlMaxRows = 75
                dynamicSqlMaxColumns = 20
                dynamicSqlMaxCellChars = 500
                dynamicSqlMaxResultChars = 16000
                dynamicSqlTimeoutMillis = 2500
                """);

    AgentSqlConfig actual = AgentSqlConfig.from(config);

    assertFalse(actual.enabled());
    assertEquals(8_000, actual.maxSqlChars());
    assertEquals(75, actual.maxRows());
    assertEquals(20, actual.maxColumns());
    assertEquals(500, actual.maxCellChars());
    assertEquals(16_000, actual.maxResultChars());
    assertEquals(Duration.ofMillis(2_500), actual.timeout());
  }

  @Test
  void environmentOverridesTomlForDynamicSqlBounds() {
    Toml config =
        new Toml()
            .read(
                """
                [agent]
                dynamicSqlEnabled = false
                dynamicSqlMaxRows = 50
                dynamicSqlTimeoutMillis = 1000
                """);

    AgentSqlConfig actual =
        AgentSqlConfig.from(
            config,
            Map.of(
                "SATURN_AGENT_DYNAMIC_SQL_ENABLED", "true",
                "SATURN_AGENT_DYNAMIC_SQL_MAX_ROWS", "25",
                "SATURN_AGENT_DYNAMIC_SQL_TIMEOUT_MILLIS", "500"));

    assertTrue(actual.enabled());
    assertEquals(25, actual.maxRows());
    assertEquals(Duration.ofMillis(500), actual.timeout());
  }

  @Test
  void rejectsNonPositiveLimits() {
    Toml config =
        new Toml()
            .read(
                """
                [agent]
                dynamicSqlMaxRows = 0
                """);

    assertThrows(IllegalArgumentException.class, () -> AgentSqlConfig.from(config));
  }

  @Test
  void rejectsLimitsOutsideIntegerRange() {
    Toml config =
        new Toml()
            .read(
                """
                [agent]
                dynamicSqlMaxRows = 2147483648
                """);

    assertThrows(IllegalArgumentException.class, () -> AgentSqlConfig.from(config));
  }
}
