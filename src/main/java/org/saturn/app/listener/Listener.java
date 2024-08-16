package org.saturn.app.listener;

public interface Listener {
  String getListenerName();
  void notify(String message);
}
