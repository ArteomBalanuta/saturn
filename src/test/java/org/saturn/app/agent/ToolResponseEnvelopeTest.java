package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonNull;
import org.junit.jupiter.api.Test;

class ToolResponseEnvelopeTest {
  @Test
  void rejectsMalformedErrorEnvelopes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ToolResponseEnvelope("error", JsonNull.INSTANCE, null));
    assertThrows(IllegalArgumentException.class, () -> ToolResponseEnvelope.error("", "failure"));
  }

  @Test
  void serializesSuccessAndErrorWithTheStableProtocolShape() {
    assertTrue(
        ToolResponseEnvelope.success("{\"count\":1}").toJson().contains("\"status\":\"success\""));
    assertTrue(
        ToolResponseEnvelope.error("TOOL_TIMEOUT", "Timed out").toJson().contains("TOOL_TIMEOUT"));
  }
}
