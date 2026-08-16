package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentToolResultTest {
  @Test
  void serializesStringAndStructuredSuccessValues() {
    AgentToolResult text = AgentToolResult.success("read", "plain output");
    AgentToolResult structured = AgentToolResult.success("read", Map.of("count", 2));

    assertEquals("plain output", text.content());
    assertFalse(text.isError());
    assertEquals("{\"count\":2}", structured.content());
    assertTrue(JsonParser.parseString(structured.content()).isJsonObject());
    assertNull(text.errorCode());
  }

  @Test
  void serializesNullSuccessAsJsonNull() {
    AgentToolResult result = AgentToolResult.success("read", null);

    assertEquals("null", result.content());
    assertEquals("{\"status\":\"success\"}", result.envelopeJson());
  }

  @Test
  void preservesCustomErrorCodeAndMessageInEnvelope() {
    AgentToolResult result =
        AgentToolResult.error("call-1", "read", "INVALID_ARGUMENTS", "bad arguments");

    assertTrue(result.isError());
    assertEquals("INVALID_ARGUMENTS", result.errorCode());
    assertEquals(
        "{\"status\":\"error\",\"error\":{\"code\":\"INVALID_ARGUMENTS\",\"message\":\"bad arguments\"}}",
        result.envelopeJson());
  }

  @Test
  void usesDefaultErrorCodeForLegacyConstructors() {
    AgentToolResult fourArgument = new AgentToolResult("call-1", "read", "failed", true);
    AgentToolResult factory = AgentToolResult.error("call-1", "read", "failed");

    assertEquals("TOOL_EXECUTION_FAILED", fourArgument.errorCode());
    assertEquals("TOOL_EXECUTION_FAILED", factory.errorCode());
  }

  @Test
  void normalizesMissingErrorCodeOnExplicitErrorResults() {
    AgentToolResult result = new AgentToolResult("call-1", "read", "failed", true, null);

    assertEquals("TOOL_EXECUTION_FAILED", result.errorCode());
    assertEquals(
        "{\"status\":\"error\",\"error\":{\"code\":\"TOOL_EXECUTION_FAILED\",\"message\":\"failed\"}}",
        result.envelopeJson());
  }

  @Test
  void withCallIdReturnsAnImmutableResultCopy() {
    AgentToolResult original = AgentToolResult.success("read", "ok");
    AgentToolResult identified = original.withCallId("call-1");

    assertNull(original.callId());
    assertEquals("call-1", identified.callId());
    assertEquals(original.toolName(), identified.toolName());
    assertEquals(original.content(), identified.content());
    assertEquals(original, original.withCallId(null));
  }
}
