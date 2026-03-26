package org.saturn.app.listener.message.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandler;

@Slf4j
public class RelayAgentMessageHandler implements ChatMessageHandler {

  @Override
  public boolean handle(ChatMessageContext context) {
    if (!context.getEngine().engineType.equals(EngineType.AGENT)) {
      return true;
    }

    EngineImpl hostRef = context.getEngine().getHostRef();
    if (hostRef == null) {
      log.warn("Host reference is null while relaying agent message");
      return false;
    }

    hostRef.outService.enqueueMessageForSending(
        context.getMessage().getNick()
            + ": "
            + StringEscapeUtils.escapeJava(context.getMessage().getText()));
    hostRef.shareMessages();
    return false;
  }
}
