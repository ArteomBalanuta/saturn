package org.saturn.app.agent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentInvocationMode;
import org.saturn.app.agent.turn.AgentToolEvidence;

class AgentRequestClassifierTest {
  private final AgentRequestClassifier classifier = new AgentRequestClassifier();

  @Test
  void classifiesOrdinaryConversationalTextAsTalk() {
    assertEquals(
        AgentRequestKind.TALK,
        classifier.classifyCandidate(
            new AgentRequestInput("hello, how are you?", AgentInvocationMode.DIRECT, false)));
    assertEquals(
        AgentRequestKind.TALK,
        classifier.classifyCandidate(
            new AgentRequestInput("The sky is blue.", AgentInvocationMode.MENTION, false)));
  }

  @Test
  void classifiesMalformedAndActionableTextAsUnclassified() {
    assertEquals(
        AgentRequestKind.UNCLASSIFIED,
        classifier.classifyCandidate(
            new AgentRequestInput("{not-json", AgentInvocationMode.DIRECT, false)));
    assertEquals(
        AgentRequestKind.UNCLASSIFIED,
        classifier.classifyCandidate(
            new AgentRequestInput("delete the old entry", AgentInvocationMode.DIRECT, false)));
    assertEquals(
        AgentRequestKind.UNCLASSIFIED,
        classifier.classifyCandidate(
            new AgentRequestInput("qz%%%", AgentInvocationMode.DIRECT, false)));
  }

  @Test
  void actualToolAttemptTakesPrecedenceOverCandidateAndFinalProse() {
    assertEquals(
        AgentRequestKind.TOOL_CALL,
        classifier.finalizeKind(AgentRequestKind.TALK, new AgentToolEvidence(true, 1, 1, 0)));
    assertEquals(
        AgentRequestKind.TOOL_CALL,
        classifier.finalizeKind(AgentRequestKind.TALK, new AgentToolEvidence(true, 1, 0, 1)));
  }
}
