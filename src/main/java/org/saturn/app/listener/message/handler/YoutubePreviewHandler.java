package org.saturn.app.listener.message.handler;

import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandler;

public class YoutubePreviewHandler implements ChatMessageHandler {

  @Override
  public boolean handle(ChatMessageContext context) {
    context.getEngine()
        .printYoutubeThumbnailAndDetails(
            context.getMessage().getNick(), context.getMessage().getText());
    return true;
  }
}
