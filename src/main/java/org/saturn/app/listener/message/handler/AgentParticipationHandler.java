package org.saturn.app.listener.message.handler;

import org.saturn.app.agent.api.AgentRoomAutomation;
import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandler;

public final class AgentParticipationHandler implements ChatMessageHandler {
  @Override
  public boolean handle(ChatMessageContext context) {
    return context.getEngine().getAgentRoomAutomation().onMessage(context.getMessage())
        == AgentRoomAutomation.Outcome.PASS;
  }
}
