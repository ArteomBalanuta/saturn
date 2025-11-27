package org.saturn.app.listener.impl;

import java.util.List;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.Util;

public class MsgChannelCommandListenerImpl implements JoinChannelListener {
  private final JoinChannelListenerDto dto;

  private ChatMessage chatMessage;

  public void setChatMessage(ChatMessage chatMessage) {
    this.chatMessage = chatMessage;
  }

  private Runnable operation;

  public MsgChannelCommandListenerImpl(JoinChannelListenerDto dto) {
    this.dto = dto;
  }

  @Override
  public String getListenerName() {
    return "msgChannelListener";
  }

  @Override
  public void notify(String jsonText) {
    List<User> users = Util.extractUsersFromJson(jsonText);
    dto.slaveEngine.setActiveUsers(users);
    EngineImpl mainEngine = dto.mainEngine;
    boolean onlyMeOnline = users.stream().allMatch(User::isIsMe);
    if (onlyMeOnline) {
      mainEngine.outService.enqueueMessageForSending(
          chatMessage.getNick(), " " + dto.channel + " is empty", chatMessage.isWhisper());
    } else {
      if (this.operation != null) {
        this.operation.run();
      }
    }

    dto.slaveEngine.stop();

    mainEngine.shareMessages();
  }

  @Override
  public void setAction(Runnable runnable) {
    this.operation = runnable;
  }
}
