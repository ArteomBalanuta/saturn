package org.saturn.app.listener.info;

import java.util.List;

public class InfoMessageHandlerChain {
  private final List<InfoMessageHandler> handlers;

  public InfoMessageHandlerChain(List<InfoMessageHandler> handlers) {
    this.handlers = handlers;
  }

  public void process(InfoMessageContext context) {
    for (InfoMessageHandler handler : handlers) {
      if (!handler.handle(context)) {
        return;
      }
    }
  }
}
