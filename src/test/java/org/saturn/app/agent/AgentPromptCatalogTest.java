package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentPromptCatalogTest {
  @Test
  void loadsToolCopyAndPreservesStructuredGuidance() {
    AgentPromptCatalog catalog = new AgentPromptCatalog();

    assertTrue(
        catalog
            .toolDescription("run_command")
            .contains("Execute exactly one approved Saturn command"));
    assertEquals(2, catalog.toolGuidance("run_command", "whenToUse").size());
    assertTrue(catalog.toolExample("run_command").contains("weather"));
  }

  @Test
  void loadsRawResourcesWithoutDiscardingWhitespace() {
    AgentPromptCatalog catalog = new AgentPromptCatalog();

    String prompt = catalog.text("vaelen-system-prompt.txt");

    assertTrue(prompt.startsWith("You are **Vaelen**"));
    assertTrue(prompt.endsWith("\n"));
  }

  @Test
  void moderationPolicyDescribesExposedCommandsAsExecutableCapabilities() {
    AgentPromptCatalog catalog = new AgentPromptCatalog();

    String policy = catalog.text("system-policy.txt");

    assertTrue(
        policy.contains(
            "When asked what you can do, state that you can execute every moderation action"
                + " currently exposed by run_command"));
  }

  @Test
  void rejectsUnknownToolCopyWithoutReturningPartialPromptData() {
    AgentPromptCatalog catalog = new AgentPromptCatalog();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> catalog.toolDescription("unknown_tool"));

    assertEquals("Missing agent tool copy: unknown_tool", exception.getMessage());
  }
}
