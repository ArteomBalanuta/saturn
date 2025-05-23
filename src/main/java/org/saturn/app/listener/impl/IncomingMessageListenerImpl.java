package org.saturn.app.listener.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;

public class IncomingMessageListenerImpl implements Listener {

  @Override
  public String getListenerName() {
    return "incomingMessageListener";
  }

  private final EngineImpl engine;

  public IncomingMessageListenerImpl(EngineImpl engine) {
    this.engine = engine;
  }

  @Override
  public void notify(String jsonText) {
    engine.dispatchMessage(jsonText);
    engine.shareMessages();
  }
}
