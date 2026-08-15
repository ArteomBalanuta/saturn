package org.saturn.app.listener.message.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.AgentRoomAutomation;
import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.support.TestSupport;

class AgentParticipationHandlerTest {
  @Test
  void translatesAutomationOutcomeIntoHandlerChainContinuation() {
    var engine = TestSupport.engine();
    AtomicReference<AgentRoomAutomation.Outcome> outcome =
        new AtomicReference<>(AgentRoomAutomation.Outcome.PASS);
    engine.setAgentRoomAutomation(message -> outcome.get());
    AgentParticipationHandler handler = new AgentParticipationHandler();
    ChatMessageContext context =
        new ChatMessageContext(engine, TestSupport.chatMessage("hello", "alice", "trip-a"));

    assertTrue(handler.handle(context));
    outcome.set(AgentRoomAutomation.Outcome.CLAIMED);
    assertFalse(handler.handle(context));
    engine.stop();
  }
}
