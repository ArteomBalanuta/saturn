package org.saturn.app.listener.impl;

import static org.saturn.app.util.Util.gson;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.listener.info.InfoMessageContext;
import org.saturn.app.listener.info.InfoMessageHandlerChain;
import org.saturn.app.listener.info.handler.AuditWhisperCommandHandler;
import org.saturn.app.listener.info.handler.CaptureBanishedUserHandler;
import org.saturn.app.listener.info.handler.ConvertWhisperToChatMessageHandler;
import org.saturn.app.listener.info.handler.DispatchWhisperCommandHandler;
import org.saturn.app.listener.info.handler.IgnoreSelfWhisperInfoHandler;
import org.saturn.app.listener.info.handler.RenameAfkUsersHandler;
import org.saturn.app.model.dto.payload.InfoMessage;

@Slf4j
public class InfoMessageListenerImpl implements Listener {
  private final EngineImpl engine;
  private final InfoMessageHandlerChain handlerChain;

  public InfoMessageListenerImpl(EngineImpl engine) {
    this.engine = engine;
    this.handlerChain =
        new InfoMessageHandlerChain(
            List.of(
                new CaptureBanishedUserHandler(),
                new IgnoreSelfWhisperInfoHandler(),
                new RenameAfkUsersHandler(),
                new ConvertWhisperToChatMessageHandler(),
                new AuditWhisperCommandHandler(),
                new DispatchWhisperCommandHandler()));
  }

  @Override
  public String getListenerName() {
    return "infoMessageListener";
  }

  @Override
  public void notify(String jsonText) {
    InfoMessage message = gson.fromJson(jsonText, InfoMessage.class);
    message.setJson(jsonText);
    handlerChain.process(new InfoMessageContext(engine, message));
  }

  public String substringFromEndUpTo(String text, String c) {
    return text.substring(text.length() - (text.length() - text.indexOf(c) - c.length()));
  }

  public String substringFromStartUpTo(String text, String c) {
    return text.substring(0, text.indexOf(c));
  }
}
