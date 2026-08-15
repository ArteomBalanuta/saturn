package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
