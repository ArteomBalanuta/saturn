package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ToolResponseEnvelopeTest {
  @Test
  void rejectsMalformedErrorEnvelopes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ToolResponseEnvelope("unknown", JsonNull.INSTANCE, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ToolResponseEnvelope(
                "success", JsonNull.INSTANCE, new ToolResponseEnvelope.Error("code", "message")));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ToolResponseEnvelope("error", JsonNull.INSTANCE, null));
    assertThrows(IllegalArgumentException.class, () -> ToolResponseEnvelope.error("", "failure"));
    assertThrows(IllegalArgumentException.class, () -> ToolResponseEnvelope.error("code", ""));
    assertThrows(IllegalArgumentException.class, () -> ToolResponseEnvelope.error(" ", "failure"));
    assertThrows(IllegalArgumentException.class, () -> ToolResponseEnvelope.error("code", " "));
  }

  @Test
  void normalizesNullDataAndParsesRawAndTextSuccessValues() {
    ToolResponseEnvelope nullData = new ToolResponseEnvelope("success", null, null);
    ToolResponseEnvelope object = ToolResponseEnvelope.success("{\"count\":1}");
    ToolResponseEnvelope text = ToolResponseEnvelope.success("not-json");

    assertTrue(nullData.data().isJsonNull());
    assertEquals(1, object.data().getAsJsonObject().get("count").getAsInt());
    assertTrue(text.data().isJsonPrimitive());
    assertEquals("not-json", text.data().getAsString());
  }

  @Test
  void serializesSuccessAndErrorWithTheStableProtocolShape() {
    assertTrue(
        ToolResponseEnvelope.success("{\"count\":1}").toJson().contains("\"status\":\"success\""));
    assertTrue(
        ToolResponseEnvelope.error("TOOL_TIMEOUT", "Timed out").toJson().contains("TOOL_TIMEOUT"));
  }

  @Test
  void isolatesMutableDataFromTheEnvelope() {
    JsonObject input = new JsonObject();
    input.addProperty("value", "original");
    ToolResponseEnvelope envelope = new ToolResponseEnvelope("success", input, null);

    input.addProperty("value", "changed");
    envelope.data().getAsJsonObject().addProperty("value", "also-changed");

    assertEquals("original", envelope.data().getAsJsonObject().get("value").getAsString());
    assertEquals("original", envelope.toJson().contains("original") ? "original" : "changed");
  }
}
