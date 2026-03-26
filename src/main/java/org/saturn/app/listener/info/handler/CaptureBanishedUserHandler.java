package org.saturn.app.listener.info.handler;

import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.listener.info.InfoMessageContext;
import org.saturn.app.listener.info.InfoMessageHandler;

public class CaptureBanishedUserHandler implements InfoMessageHandler {

  @Override
  public boolean handle(InfoMessageContext context) {
    String text = context.getInfoMessage().getText();
    if (text != null && text.contains("was banished to ?")) {
      UserCommandBaseImpl.kickedTo = substringFromEndUpTo(text, "?");
      UserCommandBaseImpl.lastKicked = substringFromStartUpTo(text, " was banished");
    }
    return true;
  }

  private String substringFromEndUpTo(String text, String c) {
    return text.substring(text.length() - (text.length() - text.indexOf(c) - c.length()));
  }

  private String substringFromStartUpTo(String text, String c) {
    return text.substring(0, text.indexOf(c));
  }
}
