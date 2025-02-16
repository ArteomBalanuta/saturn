package org.saturn.app.listener.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;

public class ConnectionListenerImpl implements Listener {
  @Override
  public String getListenerName() {
    return "connectionListener";
  }

  private final EngineImpl engine;

  public ConnectionListenerImpl(EngineImpl engine) {
    this.engine = engine;
  }

  @Override
  public void notify(String jsonText) {
    if ("connected".equals(jsonText)) {
      engine.sendJoinMessage();
      return;
    }

    engine.dispatchMessage(jsonText);
  }
}
