package org.saturn.app.listener.message.handler;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandler;

@Slf4j
public class UpdateAfkStateHandler implements ChatMessageHandler {

  @Override
  public boolean handle(ChatMessageContext context) {
    if (context.getAuthor().isEmpty()) {
      log.warn(
          "Skipping author-dependent AFK handling for nick: {}", context.getMessage().getNick());
      return true;
    }

    context.getEngine().notifyUserNotAfkAnymore(context.getAuthor().get());
    context
        .getEngine()
        .notifyIsAfkIfUserIsMentioned(
            context.getMessage().getNick(), context.getMessage().getText());
    return true;
  }
}
