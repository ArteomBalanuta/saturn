package org.saturn.app.listener.info.handler;

import org.saturn.app.listener.info.InfoMessageContext;
import org.saturn.app.listener.info.InfoMessageHandler;

public class IgnoreSelfWhisperInfoHandler implements InfoMessageHandler {

  @Override
  public boolean handle(InfoMessageContext context) {
    return !context.getInfoMessage().getText().contains("You whispered");
  }
}
