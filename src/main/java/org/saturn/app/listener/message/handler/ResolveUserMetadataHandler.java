package org.saturn.app.listener.message.handler;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandler;
import org.saturn.app.model.dto.User;

@Slf4j
public class ResolveUserMetadataHandler implements ChatMessageHandler {

  @Override
  public boolean handle(ChatMessageContext context) {
    context.setAuthor(
        context.getEngine().currentChannelUsers.stream()
            .filter(u -> u.getNick().equals(context.getMessage().getNick()))
            .findFirst());

    context.getAuthor().ifPresentOrElse(
        user -> context.getMessage().setHash(user.getHash()),
        () -> {
          log.warn("Hash for user: {}, not present ", context.getMessage().getNick());
          log.warn(
              "Active users: {}",
              context.getEngine().currentChannelUsers.stream().map(User::getNick).toList());
        });

    return true;
  }
}
