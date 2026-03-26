package org.saturn.app.listener.message.handler;

import java.util.List;
import org.saturn.app.command.UserCommand;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.listener.message.ChatMessageContext;
import org.saturn.app.listener.message.ChatMessageHandler;

public class DispatchUserCommandHandler implements ChatMessageHandler {

  @Override
  public boolean handle(ChatMessageContext context) {
    String cmd = context.getMessage().getText().trim();
    if (!cmd.startsWith(context.getEngine().prefix)) {
      return false;
    }

    UserCommand userCommand =
        new UserCommandBaseImpl(context.getMessage(), context.getEngine(), List.of());
    userCommand.execute();
    return false;
  }
}
