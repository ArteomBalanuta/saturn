package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;

class AgentResponseSanitizerTest {
  private final AgentResponseSanitizer sanitizer = new AgentResponseSanitizer();

  @Test
  void removesOnlyLegacyPersonaMarkersAndFormatsListsForSaturn() {
    String result =
        sanitizer.sanitize("[sips tea]\nAh, mer.\n* weather was sunny\nCarpe diem, mer.");

    assertEquals("\u2009-\u2009weather was sunny", result);
  }

  @Test
  void preservesOrdinaryEvidenceThatIsNotPersonaBoilerplate() {
    String result =
        sanitizer.sanitize("Relevant records reveal useful database evidence.\n* plain fact");

    assertEquals(
        "Relevant records reveal useful database evidence.\n\u2009-\u2009plain fact", result);
    assertFalse(
        sanitizer.containsLegacyPersona("Relevant records reveal useful database evidence."));
    assertTrue(sanitizer.containsLegacyPersona("[sips tea]"));
  }

  @Test
  void normalizesNullBlankAndNumberedListContent() {
    assertEquals("", sanitizer.sanitize(null));
    assertEquals("", sanitizer.sanitize(" \n\t"));
    assertEquals(
        "\u2009-\u2009first\n\u2009-\u2009second", sanitizer.sanitize("1. first\n2) second"));
  }

  @Test
  void excludesLegacyAssistantTurnsAndTheirImmediatelyPrecedingUserTurn() {
    List<LlmMessage> clean =
        sanitizer.excludeLegacyPersonaTurns(
            List.of(
                LlmMessage.user("question"),
                LlmMessage.assistant("[sips tea] legacy", List.of()),
                LlmMessage.user("ordinary"),
                LlmMessage.assistant("answer", List.of())));

    assertEquals(2, clean.size());
    assertEquals("ordinary", clean.getFirst().content());
    assertEquals("answer", clean.getLast().content());
    assertTrue(sanitizer.excludeLegacyPersonaTurns(List.of()).isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> clean.add(LlmMessage.user("mutated")));
  }
}
