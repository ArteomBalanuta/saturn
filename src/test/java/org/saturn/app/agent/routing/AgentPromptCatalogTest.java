package org.saturn.app.agent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    assertTrue(prompt.startsWith("Role & Objective:"));
    assertTrue(prompt.endsWith("\n"));
  }

  @Test
  void quoteOnlyCorrectionRequiresKnownRelatedQuoteAndRejectsOriginalAphorisms() {
    AgentPromptCatalog catalog = new AgentPromptCatalog();

    String prompt = catalog.text("router-quote-only-correction.txt");

    assertTrue(prompt.contains("Catalog Only: Return one catalog line exactly as shown"));
    assertTrue(prompt.contains("Do not invent, alter, paraphrase, or reattribute"));
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
  void policyUsesQuoteOnlyModeForAllNonCommandProse() {
    AgentPromptCatalog catalog = new AgentPromptCatalog();

    String policy = catalog.text("system-policy.txt");

    assertTrue(policy.contains("OUTPUT STYLE: Executable requests follow their tool contracts"));
    assertTrue(policy.contains("All non-command prose requests"));
    assertTrue(
        policy.contains("including technical, factual, advice, identity, and general requests"));
    assertTrue(policy.contains("the `l`/agent invocation wrapper"));
    assertFalse(
        policy.contains("Keep command results brief and answer conceptual questions directly"));
    assertFalse(policy.contains("For definition requests, answer the exact term asked about"));
  }

  @Test
  void rejectsUnknownToolCopyWithoutReturningPartialPromptData() {
    AgentPromptCatalog catalog = new AgentPromptCatalog();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> catalog.toolDescription("unknown_tool"));

    assertEquals("Missing agent tool copy: unknown_tool", exception.getMessage());
  }

  @Test
  void formatsPromptResourcesAndReportsMissingResourcesClearly() {
    AgentPromptCatalog catalog = new AgentPromptCatalog();

    assertEquals(
        "Saturn command 'weather' executed; its output was sent to the room. No other Saturn command was executed.",
        catalog.formatted("command-executed-result.txt", "weather"));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> catalog.text("missing-prompt.txt"));
    assertEquals(
        "Missing agent prompt resource: /agent/missing-prompt.txt", exception.getMessage());
  }

  @Test
  void reportsTextResourceIoFailuresWithTheOriginalCause() {
    IOException failure = new IOException("read failed");
    AgentPromptCatalog catalog =
        new AgentPromptCatalog(
            new Gson(),
            resource -> {
              if ("tool-copy.json".equals(resource)) {
                return stream("{}");
              }
              throw failure;
            });

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> catalog.text("broken.txt"));

    assertEquals("Cannot load agent prompt resource: /agent/broken.txt", exception.getMessage());
    assertEquals(failure, exception.getCause());
  }

  @Test
  void reportsToolCopyIoFailuresDuringConstruction() {
    IOException failure = new IOException("open failed");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                new AgentPromptCatalog(
                    new Gson(),
                    resource -> {
                      throw failure;
                    }));

    assertEquals(
        "Cannot load agent prompt resource: /agent/tool-copy.json", exception.getMessage());
    assertEquals(failure, exception.getCause());
  }

  @Test
  void rejectsNullToolCopyDuringConstruction() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> new AgentPromptCatalog(new Gson(), resource -> stream("null")));

    assertEquals(
        "Cannot load agent prompt resource: /agent/tool-copy.json", exception.getMessage());
  }

  @Test
  void rejectsNonObjectToolCopyEntries() {
    AgentPromptCatalog catalog =
        new AgentPromptCatalog(new Gson(), resource -> stream("{\"broken\":\"not-an-object\"}"));

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> catalog.toolDescription("broken"));

    assertEquals("Missing agent tool copy: broken", exception.getMessage());
  }

  private static ByteArrayInputStream stream(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
  }
}
