package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
}
