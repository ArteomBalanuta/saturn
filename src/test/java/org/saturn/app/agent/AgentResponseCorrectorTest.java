package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;

class AgentResponseCorrectorTest {
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
    assertTrue(requests.getLast().messages().getLast().content().contains("duplicated an earlier"));
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
}
