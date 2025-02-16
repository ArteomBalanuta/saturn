package org.saturn.app.listener.impl;

import java.util.List;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.Util;

public class KickCommandListenerImpl implements JoinChannelListener {
  private final JoinChannelListenerDto dto;

  private ChatMessage chatMessage;

  public void setChatMessage(ChatMessage chatMessage) {
    this.chatMessage = chatMessage;
  }

  private Runnable operation;

  public KickCommandListenerImpl(JoinChannelListenerDto dto) {
    this.dto = dto;
  }

  @Override
  public String getListenerName() {
    return "kickCommandListener";
  }

  @Override
  public void notify(String jsonText) {
    List<User> users = Util.extractUsersFromJson(jsonText);
    boolean targetIsOnline = users.stream().anyMatch(user -> user.getNick().equals(dto.target));

    if (targetIsOnline) {
      if (this.operation != null) {
        System.out.println("Executing runnable..");
        this.operation.run();
        System.out.println("Done executing runnable..");
      }
    } else {
      dto.mainEngine.outService.enqueueMessageForSending(
          chatMessage.getNick(),
          " @" + dto.target + " isn't in the room ?" + dto.slaveEngine.channel,
          chatMessage.isWhisper());
    }

    dto.slaveEngine.stop();
    dto.mainEngine.shareMessages();
  }

  @Override
  public void setAction(Runnable runnable) {
    this.operation = runnable;
  }
}
