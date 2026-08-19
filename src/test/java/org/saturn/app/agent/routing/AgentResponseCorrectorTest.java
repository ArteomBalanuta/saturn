package org.saturn.app.agent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentRoutingException;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.llm.LlmToolCall;

class AgentResponseCorrectorTest {
  @Test
  void acceptsQuoteOnlyWithoutLeadingDashAndRejectsLegacyLeadingDash() {
    assertTrue(
        AgentResponseCorrector.isQuoteOnly(
            "\"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\" — Pride and Prejudice, Jane Austen"));
    assertFalse(
        AgentResponseCorrector.isQuoteOnly(
            "— \"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\" — Pride and Prejudice, Jane Austen"));
  }

  @Test
  void rejectsSyntacticallyValidFabricatedQuoteWithVerifiedFallback() throws Exception {
    AgentResponseCorrector corrector =
        new AgentResponseCorrector(
            request ->
                new LlmResponse(
                    "\"A fabricated quotation\" — Imaginary Book, Imaginary Author",
                    List.of(),
                    "stop"));

    LlmResponse corrected =
        corrector.correctQuoteOnly(
            new LlmResponse(
                "\"A fabricated quotation\" — Imaginary Book, Imaginary Author", List.of(), "stop"),
            List.of(LlmMessage.user("give me a quote")),
            "request-fabricated-quote");

    assertEquals(
        "\"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\" — Pride and Prejudice, Jane Austen",
        corrected.content());
  }

  @Test
  void rejectsCorrectQuoteWithWrongAttributionWithVerifiedFallback() throws Exception {
    AgentResponseCorrector corrector =
        new AgentResponseCorrector(
            request ->
                new LlmResponse(
                    "\"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\" — Pride and Prejudice, William Shakespeare",
                    List.of(),
                    "stop"));

    LlmResponse corrected =
        corrector.correctQuoteOnly(
            new LlmResponse(
                "\"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\" — Pride and Prejudice, William Shakespeare",
                List.of(),
                "stop"),
            List.of(LlmMessage.user("give me a quote")),
            "request-wrong-attribution");

    assertEquals(
        "\"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\" — Pride and Prejudice, Jane Austen",
        corrected.content());
  }

  @Test
  void retriesStaleResponseWithoutPromptCache() throws Exception {
    String staleResponse = "Welcome back. The room is still here.";
    List<LlmRequest> requests = new ArrayList<>();
    ArrayDeque<LlmResponse> responses =
        new ArrayDeque<>(
            List.of(
                new LlmResponse(staleResponse, List.of(), "stop"),
                new LlmResponse("They are discussing the room lock.", List.of(), "stop")));
    LlmClient client =
        request -> {
          requests.add(request);
          return responses.removeFirst();
        };
    AgentResponseCorrector corrector = new AgentResponseCorrector(client);
    List<LlmMessage> history =
        List.of(
            LlmMessage.user("Public message from @alice in #programming:\nwb"),
            LlmMessage.assistant(staleResponse, List.of()));

    LlmResponse corrected =
        corrector.completeInitialRequest(
            List.of(LlmMessage.system("system"), LlmMessage.user("what is being discussed?")),
            List.of(),
            history,
            "what is being discussed?",
            "request-1");

    assertEquals("They are discussing the room lock.", corrected.content());
    assertEquals(2, requests.size());
    assertFalse(requests.getFirst().bypassPromptCache());
    assertTrue(requests.getLast().bypassPromptCache());
    assertEquals("system", requests.getLast().messages().getFirst().role());
    assertTrue(
        requests.getLast().messages().getFirst().content().contains("duplicated an earlier"));
  }

  @Test
  void staleRetryUsesNewestUserRequestWithoutReplayingStaleAssistantAnswer() throws Exception {
    String staleResponse = "The old answer";
    List<LlmRequest> requests = new ArrayList<>();
    ArrayDeque<LlmResponse> responses =
        new ArrayDeque<>(
            List.of(
                new LlmResponse(staleResponse, List.of(), "stop"),
                new LlmResponse("The new answer", List.of(), "stop")));
    AgentResponseCorrector corrector =
        new AgentResponseCorrector(
            request -> {
              requests.add(request);
              return responses.removeFirst();
            });

    corrector.completeInitialRequest(
        List.of(
            LlmMessage.system("persona"),
            LlmMessage.user("old request"),
            LlmMessage.assistant(staleResponse, List.of()),
            LlmMessage.user("new request")),
        List.of(),
        List.of(LlmMessage.user("old request"), LlmMessage.assistant(staleResponse, List.of())),
        "new request",
        "request-stale-isolation");

    List<LlmMessage> retryMessages = requests.getLast().messages();
    assertTrue(retryMessages.stream().anyMatch(message -> "new request".equals(message.content())));
    assertFalse(
        retryMessages.stream()
            .anyMatch(
                message ->
                    "assistant".equals(message.role()) && staleResponse.equals(message.content())));
    assertTrue(retryMessages.stream().anyMatch(message -> "system".equals(message.role())));
  }

  @Test
  void quoteCorrectionUsesAnIsolatedSystemContractAndNeverDeliversInvalidCorrection()
      throws Exception {
    List<LlmRequest> requests = new ArrayList<>();
    AgentResponseCorrector corrector =
        new AgentResponseCorrector(
            request -> {
              requests.add(request);
              return new LlmResponse("Still ordinary prose", List.of(), "stop");
            });

    assertThrows(
        AgentRoutingException.class,
        () ->
            corrector.correctQuoteOnly(
                new LlmResponse("Ordinary prose", List.of(), "stop"),
                List.of(LlmMessage.system("persona"), LlmMessage.user("new request")),
                "request-quote-isolation"));
    List<LlmMessage> correctionMessages = requests.getFirst().messages();
    assertEquals("system", correctionMessages.getFirst().role());
    assertTrue(correctionMessages.getFirst().content().contains("exactly one single line"));
    assertFalse(
        correctionMessages.stream()
            .anyMatch(
                message ->
                    "assistant".equals(message.role())
                        && "Ordinary prose".equals(message.content())));
  }

  @Test
  void acceptsOnlyAValidStructuredQuoteLine() throws Exception {
    AgentResponseCorrector corrector =
        new AgentResponseCorrector(
            request ->
                new LlmResponse(
                    "{\"line\":\"\\\"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\\\" — Pride and Prejudice, Jane Austen\"}",
                    List.of(),
                    "stop"));

    LlmResponse corrected =
        corrector.correctQuoteOnly(
            new LlmResponse("Ordinary prose", List.of(), "stop"),
            List.of(LlmMessage.user("new request")),
            "request-structured");

    assertEquals(
        "\"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\" — Pride and Prejudice, Jane Austen",
        corrected.content());
  }

  @Test
  void rejectsMalformedStructuredQuoteAndExtraFields() throws Exception {
    for (String content :
        List.of("not json", "{\"line\":\"quote\",\"extra\":true}", "{\"line\":7}")) {
      AgentResponseCorrector corrector =
          new AgentResponseCorrector(request -> new LlmResponse(content, List.of(), "stop"));
      assertThrows(
          AgentRoutingException.class,
          () ->
              corrector.correctQuoteOnly(
                  new LlmResponse("Ordinary prose", List.of(), "stop"),
                  List.of(LlmMessage.user("new request")),
                  "request-invalid-structured"));
    }
  }

  @Test
  void fallsBackToTextualCorrectionWhenStructuredOutputIsUnsupported() throws Exception {
    List<LlmRequest> requests = new ArrayList<>();
    AgentResponseCorrector corrector =
        new AgentResponseCorrector(
            request -> {
              requests.add(request);
              if (request.responseFormat() != null) {
                throw new org.saturn.app.agent.llm.UnsupportedResponseFormatException(
                    "unsupported");
              }
              return new LlmResponse(
                  "\"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\" — Pride and Prejudice, Jane Austen",
                  List.of(),
                  "stop");
            });

    LlmResponse corrected =
        corrector.correctQuoteOnly(
            new LlmResponse("Ordinary prose", List.of(), "stop"),
            List.of(LlmMessage.user("new request")),
            "request-fallback");

    assertEquals(
        "\"It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\" — Pride and Prejudice, Jane Austen",
        corrected.content());
    assertEquals(2, requests.size());
    assertTrue(requests.getFirst().responseFormat() != null);
    assertTrue(requests.getLast().responseFormat() == null);
  }

  @Test
  void retriesGenericAcknowledgementEvenWhenPromptBodyMatches() throws Exception {
    List<LlmRequest> requests = new ArrayList<>();
    ArrayDeque<LlmResponse> responses =
        new ArrayDeque<>(
            List.of(
                new LlmResponse("Ready.", List.of(), "stop"),
                new LlmResponse("The requested report is complete.", List.of(), "stop")));
    AgentResponseCorrector corrector =
        new AgentResponseCorrector(
            request -> {
              requests.add(request);
              return responses.removeFirst();
            });

    LlmResponse corrected =
        corrector.completeInitialRequest(
            List.of(LlmMessage.user("status")),
            List.of(),
            List.of(LlmMessage.user("status"), LlmMessage.assistant("Ready.", List.of())),
            "status",
            "request-ack");

    assertEquals("The requested report is complete.", corrected.content());
    assertEquals(2, requests.size());
    assertTrue(requests.getLast().bypassPromptCache());
  }

  @Test
  void returnsATruthfulCapabilityLimitationAfterRepeatedUnverifiedActionClaims() throws Exception {
    ArrayDeque<LlmResponse> scriptedResponses =
        new ArrayDeque<>(
            List.of(
                new LlmResponse("I will check the operation.", List.of(), "stop"),
                new LlmResponse("I will execute the measurement.", List.of(), "stop")));
    LlmClient client = request -> scriptedResponses.removeFirst();
    AgentResponseCorrector corrector = new AgentResponseCorrector(client);

    LlmResponse corrected =
        corrector.correctUnverifiedActionClaim(
            new LlmResponse("I will query the internal clock.", List.of(), "stop"),
            new java.util.ArrayList<>(),
            List.<JsonObject>of(),
            "request-1");

    assertEquals(
        "Saturn does not expose a tool for that live operation, so I cannot truthfully measure or execute it. I can use a supported command or read-only lookup if you name one.",
        corrected.content());
  }

  @Test
  void leavesVerifiedAndOrdinaryResponsesUnchanged() throws Exception {
    LlmResponse ordinary = new LlmResponse("Here is the answer.", List.of(), "stop");
    AgentResponseCorrector corrector = new AgentResponseCorrector(request -> ordinary);

    assertEquals(
        ordinary,
        corrector.correctUnverifiedActionClaim(
            ordinary, new ArrayList<>(), List.of(), "request-1"));
    assertEquals(
        ordinary, corrector.correctFailurePlaceholder(ordinary, new ArrayList<>(), "request-1"));
    assertEquals(
        ordinary, corrector.correctInternalEvidenceLeak(ordinary, new ArrayList<>(), "request-1"));
  }

  @Test
  void rejectsRepeatedFailurePlaceholderAfterCorrection() {
    LlmResponse placeholder =
        new LlmResponse("The agent could not answer that request.", List.of(), "stop");
    AgentResponseCorrector corrector = new AgentResponseCorrector(request -> placeholder);

    assertThrows(
        AgentRoutingException.class,
        () -> corrector.correctFailurePlaceholder(placeholder, new ArrayList<>(), "request-1"));
  }

  @Test
  void rejectsRepeatedInternalEvidenceAfterCorrection() {
    LlmResponse leaked =
        new LlmResponse("[Internal tool evidence from run_command] secret", List.of(), "stop");
    AgentResponseCorrector corrector = new AgentResponseCorrector(request -> leaked);

    assertThrows(
        AgentRoutingException.class,
        () -> corrector.correctInternalEvidenceLeak(leaked, new ArrayList<>(), "request-1"));
  }

  @Test
  void recognizesOnlyDocumentedFailurePlaceholdersAndActionMarkers() {
    assertTrue(
        AgentResponseCorrector.isFailurePlaceholder(
            new LlmResponse(" I COULD NOT ANSWER THAT REQUEST. ", List.of(), "stop")));
    assertFalse(
        AgentResponseCorrector.isFailurePlaceholder(
            new LlmResponse("I could not answer your request.", List.of(), "stop")));
    assertFalse(
        AgentResponseCorrector.isFailurePlaceholder(
            new LlmResponse(
                "I could not answer that request.",
                List.of(new LlmToolCall("id", "tool", "{}")),
                "tool_calls")));
  }

  @Test
  void treatsNullFailurePlaceholderResponsesAsOrdinaryResponses() {
    assertFalse(AgentResponseCorrector.isFailurePlaceholder(null));
  }

  @Test
  void leavesEmptyAndBlankActionClaimsUnchanged() throws Exception {
    AgentResponseCorrector corrector =
        new AgentResponseCorrector(request -> new LlmResponse("unexpected", List.of(), "stop"));

    assertEquals(
        new LlmResponse("", List.of(), "stop"),
        corrector.correctUnverifiedActionClaim(
            new LlmResponse("", List.of(), "stop"), new ArrayList<>(), List.of(), "request-1"));
    assertEquals(
        new LlmResponse("   ", List.of(), "stop"),
        corrector.correctUnverifiedActionClaim(
            new LlmResponse("   ", List.of(), "stop"), new ArrayList<>(), List.of(), "request-1"));
  }
}
