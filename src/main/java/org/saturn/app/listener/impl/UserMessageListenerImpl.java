package org.saturn.app.listener.impl;

import static org.saturn.app.util.Util.gson;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandlerChain;
import org.saturn.app.listener.message.handler.AuditChatMessageHandler;
import org.saturn.app.listener.message.handler.CernEasterEggHandler;
import org.saturn.app.listener.message.handler.DeliverPendingMailHandler;
import org.saturn.app.listener.message.handler.DispatchUserCommandHandler;
import org.saturn.app.listener.message.handler.IgnoreBotMessageHandler;
import org.saturn.app.listener.message.handler.LogChatMessageHandler;
import org.saturn.app.listener.message.handler.RelayAgentMessageHandler;
import org.saturn.app.listener.message.handler.ResolveUserMetadataHandler;
import org.saturn.app.listener.message.handler.UpdateAfkStateHandler;
import org.saturn.app.listener.message.handler.YoutubePreviewHandler;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
public class UserMessageListenerImpl implements Listener {

  private final EngineImpl engine;
  private final ChatMessageHandlerChain handlerChain;

  public UserMessageListenerImpl(EngineImpl engine) {
    this.engine = engine;
    this.handlerChain =
        new ChatMessageHandlerChain(
            List.of(
                new ResolveUserMetadataHandler(),
                new AuditChatMessageHandler(),
                new IgnoreBotMessageHandler(),
                new RelayAgentMessageHandler(),
                new LogChatMessageHandler(),
                new DeliverPendingMailHandler(),
                new UpdateAfkStateHandler(),
                new YoutubePreviewHandler(),
                new CernEasterEggHandler(),
                new DispatchUserCommandHandler()));
  }

  @Override
  public String getListenerName() {
    return "messageListener";
  }

  @Override
  public void notify(String jsonText) {
    log.debug("Full message payload: {}", jsonText);
    ChatMessage message = gson.fromJson(jsonText, ChatMessage.class);
    handlerChain.process(new ChatMessageContext(engine, message));
  }
}
