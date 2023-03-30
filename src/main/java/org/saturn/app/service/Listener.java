package org.saturn.app.service;

public interface Listener {
    String getListenerName();
    void notify(String message);
}
