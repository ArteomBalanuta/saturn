package org.saturn.app.agent.turn;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentRoutingException;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.llm.LlmResponse;

class AgentFreshDataFinalValidatorTest {
  @Test
  void rejectsIncompleteRequiredHistorySynthesis() {
    AgentFreshDataFinalValidator validator =
        new AgentFreshDataFinalValidator(new AgentFreshDataPolicy());

    AgentRoutingException exception =
        assertThrows(
            AgentRoutingException.class,
            () ->
                validator.validate(
                    Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY),
                    new LlmResponse("answer", List.of(), "stop"),
                    List.of()));

    assertEquals(
        "Agent did not produce a complete fresh history synthesis", exception.getMessage());
  }

  @Test
  void acceptsCompleteNonFreshAndFreshResults() {
    AgentFreshDataFinalValidator validator =
        new AgentFreshDataFinalValidator(new AgentFreshDataPolicy());

    assertDoesNotThrow(
        () ->
            validator.validate(
                Optional.empty(), new LlmResponse("answer", List.of(), "stop"), List.of()));
    assertDoesNotThrow(
        () ->
            validator.validate(
                Optional.of(AgentFreshnessPolicy.USER_MESSAGE_HISTORY),
                new LlmResponse("answer", List.of(), "stop"),
                List.of(
                    AgentToolResult.success(
                        AgentFreshnessPolicy.USER_MESSAGE_HISTORY, "{\"messages\":[]}"))));
  }
}
